package com.restaurantpos.localapi.inventory;

import com.restaurantpos.localapi.auth.CurrentActor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.inventory.InventoryDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class InventoryService {
 private final JdbcTemplate jdbc;
 public InventoryService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Transactional(readOnly=true)
 public List<BalanceView> listBalances(String search,boolean lowStockOnly,int requestedLimit){
  int limit=Math.max(1,Math.min(requestedLimit,300));
  StringBuilder sql=new StringBuilder("""
   SELECT p.id,p.product_no,p.name,p.unit,b.quantity_on_hand,b.minimum_quantity,b.updated_at
   FROM inventory_balances b JOIN products p ON p.id=b.product_id
   WHERE p.stock_tracked=TRUE
   """);
  java.util.ArrayList<Object> args=new java.util.ArrayList<>();
  if(search!=null&&!search.isBlank()){sql.append(" AND (lower(p.name) LIKE ? OR CAST(p.product_no AS TEXT) LIKE ?)");String term="%"+search.trim().toLowerCase(Locale.ROOT)+"%";args.add(term);args.add(term);}
  if(lowStockOnly)sql.append(" AND b.quantity_on_hand <= b.minimum_quantity");
  sql.append(" ORDER BY (b.quantity_on_hand <= b.minimum_quantity) DESC,p.name LIMIT ?");args.add(limit);
  return jdbc.query(sql.toString(),(rs,row)->{
   BigDecimal q=rs.getBigDecimal("quantity_on_hand"),m=rs.getBigDecimal("minimum_quantity");
   return new BalanceView(rs.getObject("id",UUID.class),rs.getLong("product_no"),rs.getString("name"),rs.getString("unit"),q,m,q.compareTo(m)<=0,rs.getObject("updated_at",OffsetDateTime.class));
  },args.toArray());
 }
 @Transactional
 public MovementView recordMovement(RecordMovementRequest request){
  ProductRow p=jdbc.query("SELECT id,name,stock_tracked FROM products WHERE id=? AND active=TRUE FOR UPDATE",rs->rs.next()?new ProductRow(rs.getObject("id",UUID.class),rs.getString("name"),rs.getBoolean("stock_tracked")):null,request.productId());
  if(p==null)throw PosException.notFound("Active product was not found.");
  if(!p.stockTracked())throw PosException.conflict("PRODUCT_NOT_STOCK_TRACKED","Enable stock tracking for this product before recording inventory movements.");
  BigDecimal delta=normalizeQuantity(request.quantityDelta());
  if(request.movementType()==MovementType.RECEIPT&&delta.signum()<0)throw PosException.badRequest("INVALID_MOVEMENT_DIRECTION","A receipt must increase stock.");
  if((request.movementType()==MovementType.CONSUMPTION||request.movementType()==MovementType.WASTE)&&delta.signum()>0)throw PosException.badRequest("INVALID_MOVEMENT_DIRECTION","Consumption and waste must decrease stock.");
  int changed=jdbc.update("UPDATE inventory_balances SET quantity_on_hand=quantity_on_hand+?,updated_at=now() WHERE product_id=? AND quantity_on_hand+?>=0",delta,p.id(),delta);
  if(changed==0)throw PosException.conflict("INSUFFICIENT_STOCK","Inventory balance is missing or this movement would make stock negative.");
  UUID id=UUID.randomUUID();
  jdbc.update("""
   INSERT INTO inventory_movements(id,product_id,movement_type,quantity_delta,reference_type,reference_id,reason,created_by)
   VALUES(?,?,?,?,'MANUAL',?,?,?)
   """,id,p.id(),request.movementType().name(),delta,id,request.reason().trim(),CurrentActor.name());
  return getMovement(id);
 }
 @Transactional
 public BalanceView setMinimum(UUID productId,BigDecimal minimumQuantity){
  BigDecimal minimum=normalizeNonNegative(minimumQuantity);
  int changed=jdbc.update("""
   UPDATE inventory_balances b SET minimum_quantity=?,updated_at=now()
   FROM products p WHERE b.product_id=p.id AND p.id=? AND p.active=TRUE AND p.stock_tracked=TRUE
   """,minimum,productId);
  if(changed==0)throw PosException.notFound("Active stock-tracked product was not found.");
  return jdbc.query("""
   SELECT p.id,p.product_no,p.name,p.unit,b.quantity_on_hand,b.minimum_quantity,b.updated_at
   FROM inventory_balances b JOIN products p ON p.id=b.product_id WHERE p.id=?
   """,rs->{if(!rs.next())return null;BigDecimal q=rs.getBigDecimal("quantity_on_hand"),m=rs.getBigDecimal("minimum_quantity");return new BalanceView(rs.getObject("id",UUID.class),rs.getLong("product_no"),rs.getString("name"),rs.getString("unit"),q,m,q.compareTo(m)<=0,rs.getObject("updated_at",OffsetDateTime.class));},productId);
 }
 @Transactional(readOnly=true)
 public List<MovementView> listMovements(UUID productId,int requestedLimit){
  int limit=Math.max(1,Math.min(requestedLimit,300));
  return jdbc.query("""
   SELECT m.id,m.product_id,p.name AS product_name,m.movement_type,m.quantity_delta,m.reason,m.created_by,m.created_at
   FROM inventory_movements m JOIN products p ON p.id=m.product_id
   WHERE m.product_id=? ORDER BY m.created_at DESC,m.id DESC LIMIT ?
   """,(rs,row)->new MovementView(rs.getObject("id",UUID.class),rs.getObject("product_id",UUID.class),rs.getString("product_name"),MovementType.valueOf(rs.getString("movement_type")),rs.getBigDecimal("quantity_delta"),rs.getString("reason"),rs.getString("created_by"),rs.getObject("created_at",OffsetDateTime.class)),productId,limit);
 }
 private MovementView getMovement(UUID id){return jdbc.query("""
  SELECT m.id,m.product_id,p.name AS product_name,m.movement_type,m.quantity_delta,m.reason,m.created_by,m.created_at
  FROM inventory_movements m JOIN products p ON p.id=m.product_id WHERE m.id=?
  """,rs->rs.next()?new MovementView(rs.getObject("id",UUID.class),rs.getObject("product_id",UUID.class),rs.getString("product_name"),MovementType.valueOf(rs.getString("movement_type")),rs.getBigDecimal("quantity_delta"),rs.getString("reason"),rs.getString("created_by"),rs.getObject("created_at",OffsetDateTime.class)):null,id);}
 private static BigDecimal normalizeQuantity(BigDecimal v){if(v==null||v.signum()==0||v.abs().compareTo(new BigDecimal("10000000"))>0||v.scale()>3||v.precision()>14)throw PosException.badRequest("INVALID_INVENTORY_QUANTITY","Quantity must be non-zero, in range, and have at most three decimal places.");return v.setScale(3);}
 private static BigDecimal normalizeNonNegative(BigDecimal v){if(v==null||v.signum()<0||v.compareTo(new BigDecimal("10000000"))>0||v.scale()>3||v.precision()>14)throw PosException.badRequest("INVALID_MINIMUM_QUANTITY","Minimum quantity must be non-negative and have at most three decimal places.");return v.setScale(3);}
 private record ProductRow(UUID id,String name,boolean stockTracked){}
}