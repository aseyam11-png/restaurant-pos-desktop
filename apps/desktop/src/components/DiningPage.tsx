import { useEffect, useState, type FormEvent } from "react";
import {
  posApi,
  type DiningTable,
  type UnassignedDineInOrder,
} from "../lib/posApi";

type Zone = { id: string; name: string; sortOrder: number; active: boolean };
type Props = { enabled: boolean; canManage: boolean };

const statusNames: Record<DiningTable["status"], string> = {
  AVAILABLE: "متاحة",
  RESERVED: "محجوزة",
  OCCUPIED: "مشغولة",
  OUT_OF_SERVICE: "خارج الخدمة",
};

export function DiningPage({ enabled, canManage }: Props) {
  const [zones, setZones] = useState<Zone[]>([]);
  const [tables, setTables] = useState<DiningTable[]>([]);
  const [unassignedOrders, setUnassignedOrders] = useState<UnassignedDineInOrder[]>([]);
  const [zoneName, setZoneName] = useState("");
  const [zoneId, setZoneId] = useState("");
  const [tableNumber, setTableNumber] = useState("1");
  const [tableLabel, setTableLabel] = useState("");
  const [seats, setSeats] = useState("2");
  const [selectedTable, setSelectedTable] = useState("");
  const [selectedOrder, setSelectedOrder] = useState("");
  const [transferTarget, setTransferTarget] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ kind: "success" | "error"; message: string } | null>(null);

  const refresh = async () => {
    const [zoneRows, tableRows, unassignedRows] = await Promise.all([
      posApi.diningZones(),
      posApi.diningTables(),
      posApi.unassignedDineInOrders(),
    ]);
    setZones(zoneRows);
    setTables(tableRows);
    setUnassignedOrders(unassignedRows);
    if (!zoneId && zoneRows.length) setZoneId(zoneRows[0].id);
  };
  useEffect(() => {
    if (enabled) void refresh().catch((error) => setNotice({ kind: "error", message: String(error) }));
  }, [enabled]);

  const createZone = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!zoneName.trim()) return;
    setBusy(true);
    try {
      const zone = await posApi.createDiningZone(zoneName.trim()) as Zone;
      setZoneName("");
      await refresh();
      setZoneId(zone.id);
      setNotice({ kind: "success", message: "تم إنشاء منطقة الطاولات." });
    } catch (error) {
      setNotice({ kind: "error", message: error instanceof Error ? error.message : "تعذر إنشاء المنطقة." });
    } finally { setBusy(false); }
  };

  const createTable = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const number = Number(tableNumber);
    const seatCount = Number(seats);
    if (!zoneId || !Number.isInteger(number) || number < 1 || !Number.isInteger(seatCount) || seatCount < 1 || seatCount > 50 || !tableLabel.trim()) {
      setNotice({ kind: "error", message: "أدخل منطقة ورقم طاولة واسمًا وعدد مقاعد صحيحًا." });
      return;
    }
    setBusy(true);
    try {
      await posApi.createDiningTable({ zoneId, tableNumber: number, label: tableLabel.trim(), seats: seatCount });
      setTableLabel("");
      await refresh();
      setNotice({ kind: "success", message: "تمت إضافة الطاولة." });
    } catch (error) {
      setNotice({ kind: "error", message: error instanceof Error ? error.message : "تعذر إضافة الطاولة." });
    } finally { setBusy(false); }
  };

  const assign = async () => {
    if (!selectedTable || !selectedOrder) {
      setNotice({ kind: "error", message: "اختر طاولة متاحة وطلبًا غير مسند." });
      return;
    }
    setBusy(true);
    try {
      await posApi.assignOrderToTable(selectedTable, selectedOrder);
      setSelectedOrder("");
      await refresh();
      setNotice({ kind: "success", message: "تم إسناد الطلب إلى الطاولة." });
    } catch (error) {
      setNotice({ kind: "error", message: error instanceof Error ? error.message : "تعذر إسناد الطلب." });
    } finally { setBusy(false); }
  };

  const transfer = async (source: DiningTable) => {
    if (!transferTarget) return;
    setBusy(true);
    try {
      await posApi.transferTableOrder(source.id, transferTarget);
      setTransferTarget("");
      await refresh();
      setNotice({ kind: "success", message: "تم نقل الطلب وتحديث حالة الطاولتين." });
    } catch (error) {
      setNotice({ kind: "error", message: error instanceof Error ? error.message : "تعذر نقل الطلب." });
    } finally { setBusy(false); }
  };

  const changeStatus = async (table: DiningTable) => {
    const next = table.status === "AVAILABLE" ? "RESERVED" : table.status === "RESERVED" || table.status === "OUT_OF_SERVICE" ? "AVAILABLE" : table.status;
    if (next === table.status) return;
    setBusy(true);
    try {
      await posApi.updateDiningTableStatus(table.id, next);
      await refresh();
    } catch (error) {
      setNotice({ kind: "error", message: error instanceof Error ? error.message : "تعذر تحديث حالة الطاولة." });
    } finally { setBusy(false); }
  };

  return <section className="page-panel operations-panel">
    <div className="panel-heading"><div><span className="workspace-kicker">FLOOR MANAGEMENT</span><h2>إدارة الطاولات والطلبات</h2></div><button className="secondary-action" onClick={() => void refresh()} disabled={!enabled || busy}>تحديث</button></div>
    {notice && <div className={`inline-notice ${notice.kind}`}>{notice.message}</div>}
    {canManage && <div className="operation-forms">
      <form className="operation-form-card" onSubmit={(event) => void createZone(event)}>
        <h3>إضافة منطقة</h3>
        <label>اسم المنطقة<input value={zoneName} onChange={(event) => setZoneName(event.target.value)} maxLength={80} required disabled={!enabled || busy} /></label>
        <button className="secondary-action form-submit" type="submit" disabled={!enabled || busy}>إنشاء منطقة</button>
      </form>
      <form className="operation-form-card" onSubmit={(event) => void createTable(event)}>
        <h3>إضافة طاولة</h3>
        <label>المنطقة<select value={zoneId} onChange={(event) => setZoneId(event.target.value)} required disabled={!enabled || busy}><option value="">اختر المنطقة</option>{zones.map((zone) => <option key={zone.id} value={zone.id}>{zone.name}</option>)}</select></label>
        <div className="form-row">
          <label>رقم الطاولة<input value={tableNumber} onChange={(event) => setTableNumber(event.target.value)} inputMode="numeric" /></label>
          <label>عدد المقاعد<input value={seats} onChange={(event) => setSeats(event.target.value)} inputMode="numeric" /></label>
        </div>
        <label>الاسم الظاهر<input value={tableLabel} onChange={(event) => setTableLabel(event.target.value)} maxLength={40} required disabled={!enabled || busy} /></label>
        <button className="primary-action" type="submit" disabled={!enabled || busy || !zones.length}>إضافة طاولة</button>
      </form>
    </div>}

    <div className="panel-subheading"><h3>إسناد طلب غير مرتبط بطاولة</h3><span>{unassignedOrders.length} طلب</span></div>
    <div className="table-assignment-controls">
      <label>الطلب<select value={selectedOrder} onChange={(event) => setSelectedOrder(event.target.value)} disabled={!enabled || busy}><option value="">اختر طلبًا</option>{unassignedOrders.map((order) => <option key={order.id} value={order.id}>#{order.orderNumber} · {order.customerName} · {order.total.toFixed(2)} {order.currency}</option>)}</select></label>
      <label>الطاولة المتاحة<select value={selectedTable} onChange={(event) => setSelectedTable(event.target.value)} disabled={!enabled || busy}><option value="">اختر طاولة</option>{tables.filter((table) => table.status === "AVAILABLE").map((table) => <option key={table.id} value={table.id}>{table.zoneName} · {table.label}</option>)}</select></label>
      <button className="primary-action" onClick={() => void assign()} disabled={!enabled || !canManage || busy || !unassignedOrders.length}>إسناد</button>
    </div>

    <div className="panel-subheading"><h3>مخطط الطاولات</h3><span>{tables.length} طاولة</span></div>
    <div className="dining-grid">
      {tables.map((table) => <article className={`dining-card ${table.status.toLowerCase()}`} key={table.id}>
        <div><span>{table.zoneName}</span><strong>{table.label}</strong></div>
        <b>{statusNames[table.status]}</b>
        <small>{table.activeOrderNumber ? `طلب #${table.activeOrderNumber}` : `${table.seats} مقاعد`}</small>
        {canManage && table.status === "OCCUPIED" && table.activeOrderId && <div className="table-transfer">
          <select value={transferTarget} onChange={(event) => setTransferTarget(event.target.value)} disabled={busy}>
            <option value="">نقل إلى طاولة متاحة</option>{tables.filter((target) => target.status === "AVAILABLE").map((target) => <option key={target.id} value={target.id}>{target.label}</option>)}
          </select>
          <button onClick={() => void transfer(table)} disabled={!transferTarget || busy}>نقل</button>
        </div>}
        {canManage && table.status !== "OCCUPIED" && <button className="table-status-action" onClick={() => void changeStatus(table)} disabled={busy}>{table.status === "AVAILABLE" ? "حجز" : "إتاحة"}</button>}
      </article>)}
      {!tables.length && <div className="empty-table">أضف منطقة وطاولات لبدء إدارة الصالة.</div>}
    </div>
  </section>;
}
