package com.restaurantpos.localapi.dining;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.dining.DiningDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class DiningService {
 private final JdbcTemplate jdbc;
 public DiningService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Transactional(readOnly=true)
 public List<ZoneView> listZones(){return jdbc.query("SELECT id,name,sort_order,active FROM dining_zones WHERE active=TRUE ORDER BY sort_order,name",(rs,row)->new ZoneView(rs.getObject("id",UUID.class),rs.getString("name"),rs.getInt("sort_order"),rs.getBoolean("active")));}
 @Transactional
 public ZoneView createZone(CreateZoneRequest r){UUID id=UUID.randomUUID();int order=r.sortOrder()==null?0:r.sortOrder();jdbc.update("INSERT INTO dining_zones(id,name,sort_order) VALUES(?,?,?)",id,r.name().trim(),order);return new ZoneView(id,r.name().trim(),order,true);}
 @Transactional
 public TableView createTable(CreateTableRequest r){
  Boolean active=jdbc.query("SELECT active FROM dining_zones WHERE id=?",rs->rs.next()?rs.getBoolean(1):null,r.zoneId());
  if(!Boolean.TRUE.equals(active))throw PosException.notFound("Active dining zone was not found.");
  UUID id=UUID.randomUUID();jdbc.update("INSERT INTO dining_tables(id,zone_id,table_number,label,seats) VALUES(?,?,?,?,?)",id,r.zoneId(),r.tableNumber(),r.label().trim(),r.seats());return getTable(id);
 }
 @Transactional(readOnly=true)
 public List<UnassignedDineInOrderView> listUnassignedDineInOrders(){
  return jdbc.query("""
   SELECT id,order_no,customer_name,total,currency,preparation_status,created_at
   FROM orders
   WHERE order_type='DINE_IN' AND dining_table_id IS NULL
     AND preparation_status NOT IN('DELIVERED','CANCELLED')
   ORDER BY created_at DESC
   LIMIT 100
   """,(rs,row)->new UnassignedDineInOrderView(
    rs.getObject("id",UUID.class),rs.getLong("order_no"),rs.getString("customer_name"),
    rs.getBigDecimal("total"),rs.getString("currency").trim(),rs.getString("preparation_status"),
    rs.getObject("created_at",OffsetDateTime.class)
   ));
 }
 @Transactional(readOnly=true)
 public List<TableView> listTables(UUID zoneId,TableStatus status){
  StringBuilder sql=new StringBuilder("""
   SELECT t.id,t.zone_id,z.name zone_name,t.table_number,t.label,t.seats,t.status,t.updated_at,
          o.id active_order_id,o.order_no active_order_number
   FROM dining_tables t JOIN dining_zones z ON z.id=t.zone_id
   LEFT JOIN orders o ON o.dining_table_id=t.id AND o.preparation_status NOT IN('DELIVERED','CANCELLED')
   WHERE t.active=TRUE AND z.active=TRUE
   """);
  java.util.ArrayList<Object> args=new java.util.ArrayList<>();
  if(zoneId!=null){sql.append(" AND t.zone_id=?");args.add(zoneId);}
  if(status!=null){sql.append(" AND t.status=?");args.add(status.name());}
  sql.append(" ORDER BY z.sort_order,t.table_number");
  return jdbc.query(sql.toString(),(rs,row)->map(rs),args.toArray());
 }
 @Transactional
 public TableView setStatus(UUID id,TableStatus next){
  TableRow t=lockTable(id);if(t==null)throw PosException.notFound("Dining table was not found.");
  if(next==TableStatus.OCCUPIED)throw PosException.badRequest("USE_ASSIGN_ORDER","Assign an active dine-in order to occupy a table.");
  if(t.status()==TableStatus.OCCUPIED)throw PosException.conflict("TABLE_ORDER_MUST_CLOSE","Complete, cancel or move the active order before changing this table status.");
  jdbc.update("UPDATE dining_tables SET status=?,updated_at=now() WHERE id=?",next.name(),id);return getTable(id);
 }
 @Transactional
 public TableView assignOrder(UUID tableId,UUID orderId){
  TableRow t=lockTable(tableId);if(t==null)throw PosException.notFound("Dining table was not found.");
  if(t.status()!=TableStatus.AVAILABLE)throw PosException.conflict("TABLE_NOT_AVAILABLE","Only an available table can receive a new order.");
  OrderRow o=lockOrder(orderId);if(o==null)throw PosException.notFound("Order was not found.");
  if(!"DINE_IN".equals(o.type()))throw PosException.conflict("ORDER_NOT_DINE_IN","Only dine-in orders can be assigned to a table.");
  if(o.tableId()!=null||List.of("CANCELLED","DELIVERED").contains(o.prep()))throw PosException.conflict("ORDER_NOT_ASSIGNABLE","This order already has a table or is no longer active.");
  int changed=jdbc.update("UPDATE orders SET dining_table_id=?,updated_at=now() WHERE id=? AND dining_table_id IS NULL",tableId,orderId);
  if(changed==0)throw PosException.conflict("ORDER_ALREADY_ASSIGNED","The order has already been assigned.");
  jdbc.update("UPDATE dining_tables SET status='OCCUPIED',updated_at=now() WHERE id=?",tableId);return getTable(tableId);
 }
 @Transactional
 public TableView transferOrder(UUID sourceId,UUID targetId){
  if(sourceId.equals(targetId))throw PosException.badRequest("SAME_TABLE","Choose another destination table.");
  List<TableRow> rows=jdbc.query("SELECT id,status FROM dining_tables WHERE id IN(?,?) ORDER BY id FOR UPDATE",(rs,row)->new TableRow(rs.getObject("id",UUID.class),TableStatus.valueOf(rs.getString("status"))),sourceId,targetId);
  if(rows.size()!=2)throw PosException.notFound("Source or destination table was not found.");
  TableRow source=rows.stream().filter(x->x.id().equals(sourceId)).findFirst().orElseThrow();
  TableRow target=rows.stream().filter(x->x.id().equals(targetId)).findFirst().orElseThrow();
  if(source.status()!=TableStatus.OCCUPIED)throw PosException.conflict("SOURCE_TABLE_NOT_OCCUPIED","The source table has no active order.");
  if(target.status()!=TableStatus.AVAILABLE)throw PosException.conflict("TARGET_TABLE_NOT_AVAILABLE","The destination table is not available.");
  UUID orderId=jdbc.query("SELECT id FROM orders WHERE dining_table_id=? AND preparation_status NOT IN('DELIVERED','CANCELLED') FOR UPDATE",rs->rs.next()?rs.getObject(1,UUID.class):null,sourceId);
  if(orderId==null)throw PosException.conflict("SOURCE_ORDER_MISSING","No active order is linked to the source table.");
  jdbc.update("UPDATE orders SET dining_table_id=?,updated_at=now() WHERE id=?",targetId,orderId);
  jdbc.update("UPDATE dining_tables SET status='AVAILABLE',updated_at=now() WHERE id=?",sourceId);
  jdbc.update("UPDATE dining_tables SET status='OCCUPIED',updated_at=now() WHERE id=?",targetId);
  return getTable(targetId);
 }
 @Transactional(readOnly=true)
 public TableView getTable(UUID id){return jdbc.query("""
  SELECT t.id,t.zone_id,z.name zone_name,t.table_number,t.label,t.seats,t.status,t.updated_at,
         o.id active_order_id,o.order_no active_order_number
  FROM dining_tables t JOIN dining_zones z ON z.id=t.zone_id
  LEFT JOIN orders o ON o.dining_table_id=t.id AND o.preparation_status NOT IN('DELIVERED','CANCELLED')
  WHERE t.id=?
  """,rs->rs.next()?map(rs):null,id);}
 private TableRow lockTable(UUID id){return jdbc.query("SELECT id,status FROM dining_tables WHERE id=? AND active=TRUE FOR UPDATE",rs->rs.next()?new TableRow(rs.getObject("id",UUID.class),TableStatus.valueOf(rs.getString("status"))):null,id);}
 private OrderRow lockOrder(UUID id){return jdbc.query("SELECT id,order_type,preparation_status,dining_table_id FROM orders WHERE id=? FOR UPDATE",rs->rs.next()?new OrderRow(rs.getString("order_type"),rs.getString("preparation_status"),rs.getObject("dining_table_id",UUID.class)):null,id);}
 private static TableView map(java.sql.ResultSet rs)throws java.sql.SQLException{long n=rs.getLong("active_order_number");if(rs.wasNull())n=0;return new TableView(rs.getObject("id",UUID.class),rs.getObject("zone_id",UUID.class),rs.getString("zone_name"),rs.getInt("table_number"),rs.getString("label"),rs.getInt("seats"),TableStatus.valueOf(rs.getString("status")),rs.getObject("active_order_id",UUID.class),n,rs.getObject("updated_at",OffsetDateTime.class));}
 private record TableRow(UUID id,TableStatus status){}
 private record OrderRow(String type,String prep,UUID tableId){}
}