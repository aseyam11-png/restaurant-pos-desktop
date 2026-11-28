package com.restaurantpos.localapi.purchases;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.cash.CashShiftService;
import com.restaurantpos.localapi.cash.CashShiftService.Direction;
import com.restaurantpos.localapi.cash.CashShiftService.MovementType;
import com.restaurantpos.localapi.orders.OrderDtos.PaymentMethod;
import com.restaurantpos.localapi.purchases.PurchaseDtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PurchaseService {
 private final JdbcTemplate jdbc;private final CashShiftService cash;private final String currency;
 public PurchaseService(JdbcTemplate jdbc,CashShiftService cash,@Value("${pos.currency:ILS}") String currency){this.jdbc=jdbc;this.cash=cash;this.currency=currency.toUpperCase(Locale.ROOT);}
 @Transactional
 public PurchaseView create(String key,CreatePurchaseRequest request){
  if(key==null||key.isBlank()||key.length()>128)throw PosException.badRequest("IDEMPOTENCY_KEY_REQUIRED","Idempotency-Key is required.");
  String hash=hash(request);
  int inserted=jdbc.update("INSERT INTO purchase_idempotency(idempotency_key,request_hash) VALUES(?,?) ON CONFLICT DO NOTHING",key,hash);
  if(inserted==0){
   Existing existing=jdbc.queryForObject("SELECT request_hash,purchase_id FROM purchase_idempotency WHERE idempotency_key=?", (rs,row)->new Existing(rs.getString(1),rs.getObject(2,UUID.class)),key);
   if(!hash.equals(existing.hash()))throw PosException.conflict("IDEMPOTENCY_KEY_REUSED","This key was already used with a different purchase request.");
   if(existing.purchaseId()==null)throw PosException.conflict("IDEMPOTENCY_IN_PROGRESS","The original purchase is still processing.");
   return get(existing.purchaseId());
  }
  String requestedCurrency=request.currency()==null?currency:request.currency().toUpperCase(Locale.ROOT);
  if(!currency.equals(requestedCurrency))throw PosException.badRequest("CURRENCY_NOT_CONFIGURED","Purchase currency must match the configured store currency.");
  if(request.supplierId()!=null){
   Boolean active=jdbc.query("SELECT active FROM suppliers WHERE id=?",rs->rs.next()?rs.getBoolean(1):null,request.supplierId());
   if(!Boolean.TRUE.equals(active))throw PosException.notFound("Active supplier was not found.");
  }
  List<PreparedLine> lines=new ArrayList<>();BigDecimal subtotal=BigDecimal.ZERO.setScale(2);
  for(PurchaseLineRequest line:request.lines()){
   ProductRow product=jdbc.query("SELECT id,product_no,name,unit,stock_tracked FROM products WHERE id=? AND active=TRUE FOR UPDATE",rs->rs.next()?new ProductRow(rs.getObject("id",UUID.class),rs.getLong("product_no"),rs.getString("name"),rs.getString("unit"),rs.getBoolean("stock_tracked")):null,line.productId());
   if(product==null)throw PosException.notFound("An active purchase product was not found.");
   if(!product.stockTracked())throw PosException.conflict("PURCHASE_PRODUCT_NOT_STOCK_TRACKED","Purchases in this workflow require stock-tracked products.");
   BigDecimal quantity=quantity(line.quantity()),cost=moneyPositive(line.unitCost());
   BigDecimal total=cost.multiply(quantity).setScale(2,RoundingMode.HALF_UP);
   subtotal=subtotal.add(total);lines.add(new PreparedLine(product,quantity,cost,total));
  }
  BigDecimal discount=money(request.discountAmount()==null?BigDecimal.ZERO:request.discountAmount());
  if(discount.compareTo(subtotal)>0)throw PosException.badRequest("DISCOUNT_EXCEEDS_SUBTOTAL","Purchase discount cannot exceed subtotal.");
  BigDecimal total=subtotal.subtract(discount).setScale(2);
  BigDecimal paid=money(request.paidAmount());
  if(paid.compareTo(total)>0)throw PosException.badRequest("PAYMENT_EXCEEDS_PURCHASE","Paid amount cannot exceed purchase total.");
  if(paid.signum()>0&&request.paymentMethod()==null)throw PosException.badRequest("PURCHASE_PAYMENT_METHOD_REQUIRED","Select a payment method when recording a paid amount.");
  if(paid.signum()==0&&request.paymentMethod()!=null)throw PosException.badRequest("UNUSED_PAYMENT_METHOD","A payment method must be omitted when no amount is paid.");
  UUID shiftId=paid.signum()>0&&request.paymentMethod()==PaymentMethod.CASH?cash.requireOpenShiftForUpdate():null;
  UUID purchaseId=UUID.randomUUID();BigDecimal due=total.subtract(paid).setScale(2);
  OffsetDateTime purchasedAt=request.purchasedAt()==null?OffsetDateTime.now():request.purchasedAt();
  jdbc.update("""
   INSERT INTO purchases(id,supplier_id,status,subtotal,discount_amount,total,paid_amount,due_amount,currency,payment_method,cash_shift_id,purchased_at,created_by)
   VALUES(?,?,'POSTED',?,?,?,?,?,?,?,?,?,?)
   """,purchaseId,request.supplierId(),subtotal,discount,total,paid,due,currency,request.paymentMethod()==null?null:request.paymentMethod().name(),shiftId,purchasedAt,request.createdBy().trim());
  for(PreparedLine line:lines){
   jdbc.update("""
    INSERT INTO purchase_lines(id,purchase_id,product_id,product_no_snapshot,product_name_snapshot,unit_snapshot,quantity,unit_cost,line_total)
    VALUES(?,?,?,?,?,?,?,?,?)
    """,UUID.randomUUID(),purchaseId,line.product().id(),line.product().number(),line.product().name(),line.product().unit(),line.quantity(),line.cost(),line.total());
   int changed=jdbc.update("UPDATE inventory_balances SET quantity_on_hand=quantity_on_hand+?,updated_at=now() WHERE product_id=?",line.quantity(),line.product().id());
   if(changed==0)throw PosException.conflict("INVENTORY_BALANCE_MISSING","Inventory balance is missing for a purchased product.");
   jdbc.update("""
    INSERT INTO inventory_movements(id,product_id,movement_type,quantity_delta,reference_type,reference_id,reason,created_by)
    VALUES(?,?,'PURCHASE',?,'PURCHASE',?,'Supplier purchase posted',?)
    """,UUID.randomUUID(),line.product().id(),line.quantity(),purchaseId,request.createdBy().trim());
  }
  if(paid.signum()>0&&request.paymentMethod()==PaymentMethod.CASH){
   cash.recordMovement(shiftId,MovementType.PURCHASE,Direction.OUT,paid,"Supplier purchase #"+purchaseNumber(purchaseId),request.createdBy().trim(),"PURCHASE",purchaseId);
  }
  jdbc.update("INSERT INTO audit_events(id,aggregate_type,aggregate_id,event_type,actor,payload) VALUES(?,'PURCHASE',?,'PURCHASE_POSTED',?,'{}'::jsonb)",UUID.randomUUID(),purchaseId,request.createdBy().trim());
  String payload=""" 
   {"eventVersion":1,"purchaseId":"%s"}
   """.formatted(purchaseId);
  jdbc.update("INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,payload) VALUES(?,'PURCHASE',?,'PURCHASE_POSTED',CAST(? AS jsonb))",UUID.randomUUID(),purchaseId,payload);
  jdbc.update("UPDATE purchase_idempotency SET purchase_id=? WHERE idempotency_key=?",purchaseId,key);
  return get(purchaseId);
 }
 @Transactional(readOnly=true)
 public PurchaseView get(UUID id){
  Header h=jdbc.query("""
   SELECT id,purchase_no,supplier_id,status,subtotal,discount_amount,total,paid_amount,due_amount,currency,payment_method,purchased_at,created_by
   FROM purchases WHERE id=?
   """,rs->rs.next()?new Header(rs.getObject("id",UUID.class),rs.getLong("purchase_no"),rs.getObject("supplier_id",UUID.class),rs.getString("status"),rs.getBigDecimal("subtotal"),rs.getBigDecimal("discount_amount"),rs.getBigDecimal("total"),rs.getBigDecimal("paid_amount"),rs.getBigDecimal("due_amount"),rs.getString("currency").trim(),rs.getString("payment_method"),rs.getObject("purchased_at",OffsetDateTime.class),rs.getString("created_by")):null,id);
  if(h==null)throw PosException.notFound("Purchase was not found.");
  List<PurchaseLineView> lines=jdbc.query("SELECT id,product_id,product_no_snapshot,product_name_snapshot,unit_snapshot,quantity,unit_cost,line_total FROM purchase_lines WHERE purchase_id=? ORDER BY id",(rs,row)->new PurchaseLineView(rs.getObject("id",UUID.class),rs.getObject("product_id",UUID.class),rs.getLong("product_no_snapshot"),rs.getString("product_name_snapshot"),rs.getString("unit_snapshot"),rs.getBigDecimal("quantity"),rs.getBigDecimal("unit_cost"),rs.getBigDecimal("line_total")),id);
  return new PurchaseView(h.id(),h.number(),h.supplierId(),h.status(),h.subtotal(),h.discount(),h.total(),h.paid(),h.due(),h.currency(),h.method()==null?null:PaymentMethod.valueOf(h.method()),h.purchasedAt(),h.createdBy(),lines);
 }
 @Transactional(readOnly=true)
 public List<PurchaseView> list(int requestedLimit){int limit=Math.max(1,Math.min(requestedLimit,200));List<UUID> ids=jdbc.query("SELECT id FROM purchases ORDER BY purchased_at DESC LIMIT ?",(rs,row)->rs.getObject(1,UUID.class),limit);return ids.stream().map(this::get).toList();}
 private long purchaseNumber(UUID id){Long n=jdbc.queryForObject("SELECT purchase_no FROM purchases WHERE id=?",Long.class,id);return n==null?0:n;}
 private static BigDecimal quantity(BigDecimal v){if(v==null||v.signum()<=0||v.scale()>3||v.precision()>12)throw PosException.badRequest("INVALID_QUANTITY","Purchase quantity must be positive with at most three decimal places.");return v.setScale(3);}
 private static BigDecimal money(BigDecimal v){if(v==null||v.signum()<0||v.scale()>2||v.precision()>14)throw PosException.badRequest("INVALID_MONEY_AMOUNT","Amount must be non-negative with at most two decimal places.");return v.setScale(2);}
 private static BigDecimal moneyPositive(BigDecimal v){BigDecimal m=money(v);if(m.signum()<=0)throw PosException.badRequest("INVALID_UNIT_COST","Unit cost must be greater than zero.");return m;}
 private static String hash(CreatePurchaseRequest r){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(r.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("SHA-256 unavailable",e);}}
 private record Existing(String hash,UUID purchaseId){}
 private record ProductRow(UUID id,long number,String name,String unit,boolean stockTracked){}
 private record PreparedLine(ProductRow product,BigDecimal quantity,BigDecimal cost,BigDecimal total){}
 private record Header(UUID id,long number,UUID supplierId,String status,BigDecimal subtotal,BigDecimal discount,BigDecimal total,BigDecimal paid,BigDecimal due,String currency,String method,OffsetDateTime purchasedAt,String createdBy){}
}