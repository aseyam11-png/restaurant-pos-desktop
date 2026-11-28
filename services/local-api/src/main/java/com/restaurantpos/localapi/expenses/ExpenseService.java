package com.restaurantpos.localapi.expenses;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.cash.CashShiftService;
import com.restaurantpos.localapi.cash.CashShiftService.Direction;
import com.restaurantpos.localapi.cash.CashShiftService.MovementType;
import com.restaurantpos.localapi.expenses.ExpenseDtos.*;
import com.restaurantpos.localapi.orders.OrderDtos.PaymentMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ExpenseService {
 private final JdbcTemplate jdbc;private final CashShiftService cash;private final String currency;
 public ExpenseService(JdbcTemplate jdbc,CashShiftService cash,@Value("${pos.currency:ILS}")String currency){this.jdbc=jdbc;this.cash=cash;this.currency=currency.toUpperCase(Locale.ROOT);}
 @Transactional
 public ExpenseView create(String key,CreateExpenseRequest request){
  if(key==null||key.isBlank()||key.length()>128)throw PosException.badRequest("IDEMPOTENCY_KEY_REQUIRED","Idempotency-Key is required.");
  String hash=hash(request);
  int inserted=jdbc.update("INSERT INTO expense_idempotency(idempotency_key,request_hash) VALUES(?,?) ON CONFLICT DO NOTHING",key,hash);
  if(inserted==0){Existing e=jdbc.queryForObject("SELECT request_hash,expense_id FROM expense_idempotency WHERE idempotency_key=?",(rs,row)->new Existing(rs.getString(1),rs.getObject(2,UUID.class)),key);if(!hash.equals(e.hash()))throw PosException.conflict("IDEMPOTENCY_KEY_REUSED","This key was already used with a different expense.");if(e.expenseId()==null)throw PosException.conflict("IDEMPOTENCY_IN_PROGRESS","The original expense is still processing.");return get(e.expenseId());}
  BigDecimal amount=money(request.amount());UUID shiftId=request.paymentMethod()==PaymentMethod.CASH?cash.requireOpenShiftForUpdate():null;UUID id=UUID.randomUUID();
  OffsetDateTime incurred=request.incurredAt()==null?OffsetDateTime.now():request.incurredAt();
  jdbc.update("INSERT INTO expenses(id,category,description,amount,currency,payment_method,cash_shift_id,incurred_at,created_by) VALUES(?,?,?,?,?,?,?,?,?)",id,request.category().trim(),request.description().trim(),amount,currency,request.paymentMethod().name(),shiftId,incurred,request.createdBy().trim());
  if(shiftId!=null)cash.recordMovement(shiftId,MovementType.EXPENSE,Direction.OUT,amount,request.description().trim(),request.createdBy().trim(),"EXPENSE",id);
  jdbc.update("INSERT INTO audit_events(id,aggregate_type,aggregate_id,event_type,actor,payload) VALUES(?,'EXPENSE',?,'EXPENSE_RECORDED',?,'{}'::jsonb)",UUID.randomUUID(),id,request.createdBy().trim());
  jdbc.update("UPDATE expense_idempotency SET expense_id=? WHERE idempotency_key=?",id,key);
  return get(id);
 }
 @Transactional(readOnly=true)
 public ExpenseView get(UUID id){return jdbc.query("SELECT id,category,description,amount,currency,payment_method,cash_shift_id,incurred_at,created_by FROM expenses WHERE id=?",rs->rs.next()?new ExpenseView(rs.getObject("id",UUID.class),rs.getString("category"),rs.getString("description"),rs.getBigDecimal("amount"),rs.getString("currency").trim(),PaymentMethod.valueOf(rs.getString("payment_method")),rs.getObject("cash_shift_id",UUID.class),rs.getObject("incurred_at",OffsetDateTime.class),rs.getString("created_by")):null,id);}
 @Transactional(readOnly=true)
 public List<ExpenseView> list(OffsetDateTime from,OffsetDateTime to,int requestedLimit){int limit=Math.max(1,Math.min(requestedLimit,300));OffsetDateTime start=from==null?OffsetDateTime.now().minusDays(30):from;OffsetDateTime end=to==null?OffsetDateTime.now().plusSeconds(1):to;if(end.isBefore(start))throw PosException.badRequest("INVALID_DATE_RANGE","End date must be after start date.");return jdbc.query("SELECT id,category,description,amount,currency,payment_method,cash_shift_id,incurred_at,created_by FROM expenses WHERE incurred_at>=? AND incurred_at<? ORDER BY incurred_at DESC LIMIT ?",(rs,row)->new ExpenseView(rs.getObject("id",UUID.class),rs.getString("category"),rs.getString("description"),rs.getBigDecimal("amount"),rs.getString("currency").trim(),PaymentMethod.valueOf(rs.getString("payment_method")),rs.getObject("cash_shift_id",UUID.class),rs.getObject("incurred_at",OffsetDateTime.class),rs.getString("created_by")),start,end,limit);}
 private static BigDecimal money(BigDecimal v){if(v==null||v.signum()<=0||v.scale()>2||v.precision()>14)throw PosException.badRequest("INVALID_MONEY_AMOUNT","Expense amount must be positive with at most two decimal places.");return v.setScale(2);}
 private static String hash(CreateExpenseRequest r){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(r.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("SHA-256 unavailable",e);}}
 private record Existing(String hash,UUID expenseId){}
}