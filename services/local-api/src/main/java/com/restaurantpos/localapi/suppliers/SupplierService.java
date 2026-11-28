package com.restaurantpos.localapi.suppliers;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.suppliers.SupplierDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class SupplierService {
 private final JdbcTemplate jdbc;
 public SupplierService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Transactional
 public SupplierView create(CreateSupplierRequest request){
  UUID id=UUID.randomUUID();
  jdbc.update("INSERT INTO suppliers(id,name,phone,email,notes) VALUES(?,?,?,?,?)",id,request.name().trim(),clean(request.phone()),clean(request.email()),clean(request.notes()));
  return get(id);
 }
 @Transactional(readOnly=true)
 public List<SupplierView> list(String query,int requestedLimit){
  int limit=Math.max(1,Math.min(requestedLimit,200));
  if(query==null||query.isBlank()){
   return jdbc.query("SELECT id,name,phone,email,notes,active,created_at FROM suppliers WHERE active=TRUE ORDER BY name LIMIT ?",this::map,limit);
  }
  String term="%"+query.trim().toLowerCase(java.util.Locale.ROOT)+"%";
  return jdbc.query("SELECT id,name,phone,email,notes,active,created_at FROM suppliers WHERE active=TRUE AND (lower(name) LIKE ? OR phone LIKE ?) ORDER BY name LIMIT ?",this::map,term,term,limit);
 }
 @Transactional
 public void deactivate(UUID id){
  int changed=jdbc.update("UPDATE suppliers SET active=FALSE,updated_at=now() WHERE id=? AND active=TRUE",id);
  if(changed==0)throw PosException.notFound("Active supplier was not found.");
 }
 @Transactional(readOnly=true)
 public SupplierView get(UUID id){
  return jdbc.query("SELECT id,name,phone,email,notes,active,created_at FROM suppliers WHERE id=?",rs->rs.next()?map(rs,0):null,id);
 }
 private SupplierView map(java.sql.ResultSet rs,int row)throws java.sql.SQLException{
  return new SupplierView(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("phone"),rs.getString("email"),rs.getString("notes"),rs.getBoolean("active"),rs.getObject("created_at",OffsetDateTime.class));
 }
 private static String clean(String v){return v==null||v.isBlank()?null:v.trim();}
}