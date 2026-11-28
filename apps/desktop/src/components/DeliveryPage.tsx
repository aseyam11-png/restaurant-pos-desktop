import { useEffect, useMemo, useState } from "react";
import { posApi, type DeliveryRider, type OrderView } from "../lib/posApi";

type Props = { enabled: boolean };

export function DeliveryPage({ enabled }: Props) {
  const [orders, setOrders] = useState<OrderView[]>([]);
  const [riders, setRiders] = useState<DeliveryRider[]>([]);
  const [selectedOrder, setSelectedOrder] = useState("");
  const [selectedRider, setSelectedRider] = useState("");
  const [address, setAddress] = useState("");
  const [phone, setPhone] = useState("");
  const [riderName, setRiderName] = useState("");
  const [riderPhone, setRiderPhone] = useState("");
  const [notice, setNotice] = useState<{ kind: "success" | "error"; message: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const pendingOrders = useMemo(
    () => orders.filter((order) => order.orderType === "DELIVERY" && !["DELIVERED", "CANCELLED"].includes(order.preparationStatus)),
    [orders],
  );

  const refresh = async () => {
    const [orderRows, riderRows] = await Promise.all([posApi.orders(200), posApi.deliveryRiders()]);
    setOrders(orderRows);
    setRiders(riderRows);
    if (selectedOrder && !orderRows.some((order) => order.id === selectedOrder)) setSelectedOrder("");
  };

  useEffect(() => {
    if (enabled) void refresh().catch((error) => setNotice({ kind: "error", message: String(error) }));
  }, [enabled]);

  const createRider = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!riderName.trim() || !riderPhone.trim()) return;
    setBusy(true);
    try {
      const rider = await posApi.createDeliveryRider({ name: riderName.trim(), phone: riderPhone.trim() });
      setRiders((rows) => [...rows, rider].sort((a, b) => a.name.localeCompare(b.name)));
      setSelectedRider(rider.id);
      setRiderName("");
      setRiderPhone("");
      setNotice({ kind: "success", message: "تم تسجيل سائق التوصيل." });
    } catch (error) {
      setNotice({ kind: "error", message: error instanceof Error ? error.message : "تعذر تسجيل السائق." });
    } finally { setBusy(false); }
  };

  const assign = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedOrder || !address.trim() || !phone.trim()) {
      setNotice({ kind: "error", message: "اختر الطلب وأدخل العنوان ورقم هاتف العميل." });
      return;
    }
    setBusy(true);
    try {
      await posApi.assignDeliveryRider(selectedOrder, {
        riderId: selectedRider || null,
        address: address.trim(),
        phone: phone.trim(),
      });
      setNotice({ kind: "success", message: "تم حفظ بيانات التوصيل وتعيين السائق." });
      setAddress("");
      setPhone("");
      await refresh();
    } catch (error) {
      setNotice({ kind: "error", message: error instanceof Error ? error.message : "تعذر تعيين السائق." });
    } finally { setBusy(false); }
  };

  const deactivate = async (rider: DeliveryRider) => {
    if (!window.confirm(`تعطيل السائق ${rider.name}؟`)) return;
    setBusy(true);
    try {
      await posApi.deactivateDeliveryRider(rider.id);
      await refresh();
      setNotice({ kind: "success", message: "تم تعطيل السائق. تبقى إسناداته السابقة محفوظة." });
    } catch (error) {
      setNotice({ kind: "error", message: error instanceof Error ? error.message : "تعذر تعطيل السائق." });
    } finally { setBusy(false); }
  };

  return <section className="page-panel operations-panel">
    <div className="panel-heading"><div><span className="workspace-kicker">LAST-MILE OPERATIONS</span><h2>إدارة التوصيل والسائقين</h2></div><button className="secondary-action" onClick={() => void refresh()} disabled={!enabled || busy}>تحديث</button></div>
    {notice && <div className={`inline-notice ${notice.kind}`}>{notice.message}</div>}
    <div className="operation-forms">
      <form className="operation-form-card" onSubmit={(event) => void assign(event)}>
        <h3>إسناد طلب توصيل</h3>
        <label>الطلب
          <select value={selectedOrder} onChange={(event) => setSelectedOrder(event.target.value)} required disabled={!enabled || busy}>
            <option value="">اختر طلب توصيل نشطًا</option>
            {pendingOrders.map((order) => <option key={order.id} value={order.id}>#{order.orderNumber} · {order.customerName} · {order.total.toFixed(2)} {order.currency}</option>)}
          </select>
        </label>
        <label>السائق
          <select value={selectedRider} onChange={(event) => setSelectedRider(event.target.value)} disabled={!enabled || busy}>
            <option value="">لم يحدد بعد</option>{riders.map((rider) => <option key={rider.id} value={rider.id}>{rider.name} · {rider.phone}</option>)}
          </select>
        </label>
        <label>عنوان التوصيل<input value={address} onChange={(event) => setAddress(event.target.value)} maxLength={240} required disabled={!enabled || busy} /></label>
        <label>هاتف العميل<input value={phone} onChange={(event) => setPhone(event.target.value)} maxLength={32} required disabled={!enabled || busy} /></label>
        <button className="primary-action" type="submit" disabled={!enabled || busy || !pendingOrders.length}>حفظ إسناد التوصيل</button>
      </form>
      <form className="operation-form-card" onSubmit={(event) => void createRider(event)}>
        <h3>إضافة سائق</h3>
        <label>اسم السائق<input value={riderName} onChange={(event) => setRiderName(event.target.value)} maxLength={120} required disabled={!enabled || busy} /></label>
        <label>رقم الهاتف<input value={riderPhone} onChange={(event) => setRiderPhone(event.target.value)} maxLength={32} required disabled={!enabled || busy} /></label>
        <button className="secondary-action form-submit" type="submit" disabled={!enabled || busy}>إضافة السائق</button>
        <p className="form-hint">لا يوجد تتبع GPS في هذا الإصدار. التعيين وحفظ العنوان والهاتف فقط.</p>
      </form>
    </div>
    <div className="panel-subheading"><h3>السائقون المسجلون</h3><span>{riders.length} سائق</span></div>
    <div className="table-scroll"><table><thead><tr><th>السائق</th><th>الهاتف</th><th>الحالة</th><th>الإجراء</th></tr></thead><tbody>
      {riders.map((rider) => <tr key={rider.id}><td>{rider.name}</td><td>{rider.phone}</td><td><span className={rider.active ? "state-chip" : "state-chip warning"}>{rider.active ? "نشط" : "معطل"}</span></td><td>{rider.active ? <button className="secondary-action" onClick={() => void deactivate(rider)} disabled={!enabled || busy}>تعطيل</button> : "—"}</td></tr>)}
      {!riders.length && <tr><td colSpan={4} className="empty-table">لم تتم إضافة سائقين بعد.</td></tr>}
    </tbody></table></div>
    <div className="panel-subheading"><h3>طلبات التوصيل النشطة</h3><span>{pendingOrders.length} طلب</span></div>
    <div className="dining-grid delivery-order-grid">
      {pendingOrders.map((order) => <article className="delivery-order-card" key={order.id}>
        <div><small>طلب #{order.orderNumber}</small><strong>{order.customerName}</strong></div>
        <b>{order.preparationStatus}</b>
        <span>{order.total.toFixed(2)} {order.currency}</span>
      </article>)}
      {!pendingOrders.length && <div className="empty-table">لا توجد طلبات توصيل نشطة.</div>}
    </div>
  </section>;
}
