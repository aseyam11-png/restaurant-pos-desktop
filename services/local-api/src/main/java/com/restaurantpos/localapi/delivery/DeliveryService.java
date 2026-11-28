package com.restaurantpos.localapi.delivery;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.delivery.DeliveryDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class DeliveryService {
 private final JdbcTemplate jdbc;
 public DeliveryService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Transactional
 public RiderView createRider(CreateRiderRequest r){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO delivery_riders(id,name,phone,notes) VALUES(?,?,?,?)",id,r.name().trim(),r.phone().trim(),r.notes()==null?null:r.notes().trim());return getRider(id);}
 @Transactional(readOnly=true)
 public List<RiderView> listRiders(String q){
  if(q==null||q.isBlank())return jdbc.query("SELECT id,name,phone,notes,active,created_at FROM delivery_riders WHERE active=TRUE ORDER BY name",this::map);
  String term="%"+q.trim().toLowerCase(java.util.Locale.ROOT)+"%";
  return jdbc.query("SELECT id,name,phone,notes,active,created_at FROM delivery_riders WHERE active=TRUE AND (lower(name) LIKE ? OR phone LIKE ?) ORDER BY name",this::map,term,term);
 }
 @Transactional
 public void deactivate(UUID id){int n=jdbc.update("UPDATE delivery_riders SET active=FALSE,updated_at=now() WHERE id=? AND active=TRUE",id);if(n==0)throw PosException.notFound("Active rider was not found.");}
 @Transactional
 public DeliveryAssignmentView assign(UUID orderId,AssignRiderRequest r){
  OrderRow order=jdbc.query("SELECT id,order_no,order_type,preparation_status FROM orders WHERE id=? FOR UPDATE",rs->rs.next()?new OrderRow(rs.getLong("order_no"),rs.getString("order_type"),rs.getString("preparation_status")):null,orderId);
  if(order==null)throw PosException.notFound("Order was not found.");
  if(!"DELIVERY".equals(order.type()))throw PosException.conflict("ORDER_NOT_DELIVERY","Only delivery orders can be assigned to a rider.");
  if(List.of("CANCELLED","DELIVERED").contains(order.status()))throw PosException.conflict("ORDER_NOT_ACTIVE","A cancelled or delivered order cannot be assigned.");
  if(r.riderId()!=null){Boolean active=jdbc.query("SELECT active FROM delivery_riders WHERE id=?",rs->rs.next()?rs.getBoolean(1):null,r.riderId());if(!Boolean.TRUE.equals(active))throw PosException.notFound("Active delivery rider was not found.");}
  jdbc.update("UPDATE orders SET delivery_rider_id=?,delivery_address=?,delivery_phone=?,updated_at=now() WHERE id=?",r.riderId(),r.address().trim(),r.phone().trim(),orderId);
  return getAssignment(orderId);
 }
 @Transactional(readOnly=true)
 public DeliveryAssignmentView getAssignment(UUID id){return jdbc.query("""
  SELECT o.id,o.order_no,o.delivery_rider_id,r.name rider_name,r.phone rider_phone,o.delivery_address,o.delivery_phone
  FROM orders o LEFT JOIN delivery_riders r ON r.id=o.delivery_rider_id WHERE o.id=?
  """,rs->rs.next()?new DeliveryAssignmentView(rs.getObject("id",UUID.class),rs.getLong("order_no"),rs.getObject("delivery_rider_id",UUID.class),rs.getString("rider_name"),rs.getString("rider_phone"),rs.getString("delivery_address"),rs.getString("delivery_phone")):null,id);}
 private RiderView getRider(UUID id){return jdbc.query("SELECT id,name,phone,notes,active,created_at FROM delivery_riders WHERE id=?",rs->rs.next()?map(rs,0):null,id);}
 private RiderView map(java.sql.ResultSet rs,int row)throws java.sql.SQLException{return new RiderView(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("phone"),rs.getString("notes"),rs.getBoolean("active"),rs.getObject("created_at",OffsetDateTime.class));}
 private record OrderRow(long orderNumber,String type,String status){}
}