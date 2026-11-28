import { useEffect, useState } from "react";
import {
  posApi,
  type CashMovement,
  type ExpenseView,
  type PaymentMethod,
  type Product,
  type PurchaseView,
  type Supplier,
} from "../lib/posApi";

type PageProps = {
  enabled: boolean;
  currency: string;
  onChanged: () => void;
};

function money(value: number, currency: string) {
  return new Intl.NumberFormat("ar-PS", { style: "currency", currency, maximumFractionDigits: 2 }).format(value || 0);
}
function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "تعذر تنفيذ العملية.";
}
function KeyedNotice({ notice }: { notice: { kind: "success" | "error"; message: string } | null }) {
  return notice ? <div className={`inline-notice ${notice.kind}`}>{notice.message}</div> : null;
}

export function PurchasesPage({ enabled, currency, onChanged }: PageProps) {
  const [products, setProducts] = useState<Product[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [purchases, setPurchases] = useState<PurchaseView[]>([]);
  const [productId, setProductId] = useState("");
  const [supplierId, setSupplierId] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [unitCost, setUnitCost] = useState("");
  const [supplierName, setSupplierName] = useState("");
  const [supplierPhone, setSupplierPhone] = useState("");
  const [settlement, setSettlement] = useState<Record<string, string>>({});
  const [settlementMethod, setSettlementMethod] = useState<PaymentMethod>("CASH");
  const [notice, setNotice] = useState<{ kind: "success" | "error"; message: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = async () => {
    const [stockProducts, supplierRows, purchaseRows] = await Promise.all([
      posApi.products(),
      posApi.suppliers(),
      posApi.purchases(100),
    ]);
    setProducts(stockProducts.filter((product) => product.stockTracked && product.active));
    setSuppliers(supplierRows);
    setPurchases(purchaseRows);
    if (!productId && stockProducts.some((product) => product.stockTracked)) {
      setProductId(stockProducts.find((product) => product.stockTracked && product.active)?.id || "");
    }
  };

  useEffect(() => {
    if (enabled) void refresh().catch((error) => setNotice({ kind: "error", message: errorMessage(error) }));
  }, [enabled]);

  const createSupplier = async () => {
    if (!supplierName.trim()) return setNotice({ kind: "error", message: "أدخل اسم المورد." });
    setBusy(true);
    try {
      const created = await posApi.createSupplier({ name: supplierName.trim(), phone: supplierPhone.trim() || undefined });
      setSuppliers((rows) => [...rows, created]);
      setSupplierId(created.id);
      setSupplierName("");
      setSupplierPhone("");
      setNotice({ kind: "success", message: "تم تسجيل المورد." });
    } catch (error) {
      setNotice({ kind: "error", message: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  const createPurchase = async () => {
    const qty = Number(quantity);
    const cost = Number(unitCost);
    if (!productId) return setNotice({ kind: "error", message: "اختر مادة مخزنية." });
    if (!Number.isFinite(qty) || qty <= 0 || !/^\d+(\.\d{1,3})?$/.test(quantity.trim())) {
      return setNotice({ kind: "error", message: "الكمية يجب أن تكون موجبة وبحد أقصى 3 منازل عشرية." });
    }
    if (!Number.isFinite(cost) || cost <= 0 || !/^\d+(\.\d{1,2})?$/.test(unitCost.trim())) {
      return setNotice({ kind: "error", message: "أدخل تكلفة صحيحة وبحد أقصى منزلتين عشريتين." });
    }
    setBusy(true);
    try {
      await posApi.createPurchase({
        supplierId: supplierId || null,
        lines: [{ productId, quantity: qty, unitCost: cost }],
        discountAmount: 0,
        paidAmount: 0,
        paymentMethod: null,
        currency,
        createdBy: "Local operator",
        purchasedAt: new Date().toISOString(),
      }, crypto.randomUUID());
      setQuantity("1");
      setUnitCost("");
      await refresh();
      onChanged();
      setNotice({ kind: "success", message: "تم حفظ فاتورة الشراء وزيادة رصيد المخزون." });
    } catch (error) {
      setNotice({ kind: "error", message: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  const paySupplier = async (purchase: PurchaseView) => {
    const amount = Number(settlement[purchase.id] || 0);
    if (!Number.isFinite(amount) || amount <= 0 || amount > purchase.dueAmount) {
      return setNotice({ kind: "error", message: "أدخل مبلغًا موجبًا لا يتجاوز الرصيد المستحق." });
    }
    setBusy(true);
    try {
      await posApi.supplierPayment(purchase.id, { amount, method: settlementMethod, recordedBy: "Local operator" }, crypto.randomUUID());
      setSettlement((values) => ({ ...values, [purchase.id]: "" }));
      await refresh();
      onChanged();
      setNotice({ kind: "success", message: "تم تسجيل دفعة المورد وتحديث الرصيد المستحق." });
    } catch (error) {
      setNotice({ kind: "error", message: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  return <section className="page-panel operations-panel">
    <div className="panel-heading"><div><span className="workspace-kicker">SUPPLIER MANAGEMENT</span><h2>المشتريات والموردون</h2></div><button className="secondary-action" onClick={() => void refresh()} disabled={!enabled || busy}>تحديث</button></div>
    <KeyedNotice notice={notice} />
    <div className="operation-forms">
      <div className="operation-form-card">
        <h3>تسجيل فاتورة شراء</h3>
        <label>المادة المخزنية
          <select value={productId} onChange={(e) => setProductId(e.target.value)} disabled={!enabled || busy}>
            <option value="">اختر المنتج</option>{products.map((p) => <option key={p.id} value={p.id}>{p.name} · #{p.productNumber}</option>)}
          </select>
        </label>
        <label>المورد (اختياري)
          <select value={supplierId} onChange={(e) => setSupplierId(e.target.value)} disabled={!enabled || busy}>
            <option value="">بدون مورد محدد</option>{suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </label>
        <div className="form-row">
          <label>الكمية<input value={quantity} onChange={(e) => setQuantity(e.target.value)} inputMode="decimal" disabled={!enabled || busy} /></label>
          <label>تكلفة الوحدة<input value={unitCost} onChange={(e) => setUnitCost(e.target.value)} inputMode="decimal" placeholder="0.00" disabled={!enabled || busy} /></label>
        </div>
        <button className="primary-action" onClick={() => void createPurchase()} disabled={!enabled || busy || products.length === 0}>حفظ فاتورة الشراء</button>
        <p className="form-hint">يتم ترحيل الفاتورة وحركة المخزون معًا داخل معاملة قاعدة بيانات واحدة. تسجيل دفعة المورد يتم من سجل الفواتير.</p>
      </div>
      <div className="operation-form-card">
        <h3>إضافة مورد</h3>
        <label>اسم المورد<input value={supplierName} onChange={(e) => setSupplierName(e.target.value)} disabled={!enabled || busy} /></label>
        <label>رقم الهاتف<input value={supplierPhone} onChange={(e) => setSupplierPhone(e.target.value)} disabled={!enabled || busy} /></label>
        <button className="secondary-action form-submit" onClick={() => void createSupplier()} disabled={!enabled || busy}>حفظ المورد</button>
        <div className="supplier-count"><strong>{suppliers.length}</strong><span>مورد نشط</span></div>
      </div>
    </div>
    <div className="panel-subheading"><h3>سجل فواتير الشراء</h3><span>{purchases.length} فاتورة</span></div>
    <div className="table-scroll"><table><thead><tr><th>الفاتورة</th><th>المورد</th><th>الإجمالي</th><th>المدفوع</th><th>المستحق</th><th>الحالة</th><th>تسديد دفعة</th></tr></thead><tbody>
      {purchases.map((purchase) => <tr key={purchase.id}>
        <td>#{purchase.purchaseNumber}</td>
        <td>{suppliers.find((s) => s.id === purchase.supplierId)?.name || "—"}</td>
        <td>{money(purchase.total, purchase.currency)}</td>
        <td>{money(purchase.paidAmount, purchase.currency)}</td>
        <td>{money(purchase.dueAmount, purchase.currency)}</td>
        <td><span className="state-chip">{purchase.paymentStatus}</span></td>
        <td>{purchase.dueAmount > 0 ? <div className="settlement-cell"><input aria-label="مبلغ الدفعة" value={settlement[purchase.id] || ""} onChange={(e) => setSettlement((x) => ({ ...x, [purchase.id]: e.target.value }))} placeholder="المبلغ" /><select value={settlementMethod} onChange={(e) => setSettlementMethod(e.target.value as PaymentMethod)}><option value="CASH">نقدي</option><option value="WALLET">محفظة</option><option value="BANK_CARD">بطاقة</option></select><button onClick={() => void paySupplier(purchase)} disabled={!enabled || busy}>تسجيل</button></div> : "مكتملة"}</td>
      </tr>)}
      {!purchases.length && <tr><td colSpan={7} className="empty-table">لا توجد فواتير مشتريات.</td></tr>}
    </tbody></table></div>
  </section>;
}

export function ExpensesPage({ enabled, currency, onChanged }: PageProps) {
  const [expenses, setExpenses] = useState<ExpenseView[]>([]);
  const [category, setCategory] = useState("تشغيل");
  const [description, setDescription] = useState("");
  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [notice, setNotice] = useState<{ kind: "success" | "error"; message: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = async () => setExpenses(await posApi.expenses(100));
  useEffect(() => {
    if (enabled) void refresh().catch((error) => setNotice({ kind: "error", message: errorMessage(error) }));
  }, [enabled]);

  const create = async () => {
    const value = Number(amount);
    if (!category.trim() || !description.trim()) return setNotice({ kind: "error", message: "الفئة والوصف مطلوبان." });
    if (!Number.isFinite(value) || value <= 0 || !/^\d+(\.\d{1,2})?$/.test(amount.trim())) {
      return setNotice({ kind: "error", message: "أدخل مبلغًا موجبًا وبحد أقصى منزلتين عشريتين." });
    }
    setBusy(true);
    try {
      await posApi.createExpense({
        category: category.trim(),
        description: description.trim(),
        amount: value,
        paymentMethod: method,
        incurredAt: new Date().toISOString(),
        createdBy: "Local operator",
      }, crypto.randomUUID());
      setDescription("");
      setAmount("");
      await refresh();
      onChanged();
      setNotice({ kind: "success", message: "تم تسجيل المصروف." });
    } catch (error) {
      setNotice({ kind: "error", message: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  return <section className="page-panel operations-panel">
    <div className="panel-heading"><div><span className="workspace-kicker">OPERATING COSTS</span><h2>المصروفات</h2></div><button className="secondary-action" onClick={() => void refresh()} disabled={!enabled || busy}>تحديث</button></div>
    <KeyedNotice notice={notice} />
    <div className="operation-form-card expense-form">
      <h3>تسجيل مصروف جديد</h3>
      <div className="form-row three">
        <label>الفئة<input value={category} onChange={(e) => setCategory(e.target.value)} disabled={!enabled || busy} /></label>
        <label>الوصف<input value={description} onChange={(e) => setDescription(e.target.value)} disabled={!enabled || busy} /></label>
        <label>المبلغ ({currency})<input value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="decimal" disabled={!enabled || busy} /></label>
      </div>
      <div className="form-row">
        <label>طريقة الدفع<select value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)} disabled={!enabled || busy}><option value="CASH">نقدي</option><option value="WALLET">محفظة</option><option value="BANK_CARD">بطاقة</option></select></label>
        <div className="form-action"><button className="primary-action" onClick={() => void create()} disabled={!enabled || busy}>حفظ المصروف</button></div>
      </div>
      {method === "CASH" && <p className="form-hint">المصروف النقدي يُخصم من الوردية المفتوحة ويُسجل في دفتر الصندوق.</p>}
    </div>
    <div className="panel-subheading"><h3>سجل المصروفات</h3><span>{expenses.length} حركة</span></div>
    <div className="table-scroll"><table><thead><tr><th>التاريخ</th><th>الفئة</th><th>الوصف</th><th>طريقة الدفع</th><th>المبلغ</th></tr></thead><tbody>
      {expenses.map((expense) => <tr key={expense.id}><td>{new Date(expense.incurredAt).toLocaleString("ar-PS")}</td><td>{expense.category}</td><td>{expense.description}</td><td>{expense.paymentMethod}</td><td>{money(expense.amount, expense.currency)}</td></tr>)}
      {!expenses.length && <tr><td colSpan={5} className="empty-table">لا توجد مصروفات مسجلة.</td></tr>}
    </tbody></table></div>
  </section>;
}

export function CashPage({ enabled, currency, onChanged }: PageProps) {
  const [shift, setShift] = useState<CashShift | null>(null);
  const [movements, setMovements] = useState<CashMovement[]>([]);
  const [opening, setOpening] = useState("0");
  const [counted, setCounted] = useState("");
  const [movementType, setMovementType] = useState<"CASH_IN" | "CASH_OUT">("CASH_IN");
  const [movementAmount, setMovementAmount] = useState("");
  const [movementDescription, setMovementDescription] = useState("");
  const [notice, setNotice] = useState<{ kind: "success" | "error"; message: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = async () => {
    const active = await posApi.activeCashShift();
    setShift(active);
    setMovements(active ? await posApi.cashMovements(active.id) : []);
  };
  useEffect(() => {
    if (enabled) void refresh().catch((error) => setNotice({ kind: "error", message: errorMessage(error) }));
  }, [enabled]);

  const open = async () => {
    const value = Number(opening);
    if (!Number.isFinite(value) || value < 0 || !/^\d+(\.\d{1,2})?$/.test(opening.trim())) {
      return setNotice({ kind: "error", message: "أدخل رصيدًا افتتاحيًا صحيحًا." });
    }
    setBusy(true);
    try {
      await posApi.openCashShift(value);
      await refresh();
      onChanged();
      setNotice({ kind: "success", message: "تم فتح وردية الصندوق." });
    } catch (error) {
      setNotice({ kind: "error", message: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  const addMovement = async () => {
    const value = Number(movementAmount);
    if (!Number.isFinite(value) || value <= 0 || !/^\d+(\.\d{1,2})?$/.test(movementAmount.trim()) || !movementDescription.trim()) {
      return setNotice({ kind: "error", message: "أدخل مبلغًا ووصفًا صحيحين." });
    }
    setBusy(true);
    try {
      await posApi.addCashMovement({ type: movementType, amount: value, description: movementDescription.trim(), createdBy: "Local operator" });
      setMovementAmount("");
      setMovementDescription("");
      await refresh();
      onChanged();
      setNotice({ kind: "success", message: "تم تسجيل حركة الصندوق." });
    } catch (error) {
      setNotice({ kind: "error", message: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  const close = async () => {
    if (!shift) return;
    const value = Number(counted);
    if (!Number.isFinite(value) || value < 0 || !/^\d+(\.\d{1,2})?$/.test(counted.trim())) {
      return setNotice({ kind: "error", message: "أدخل النقد المعدود قبل إغلاق الوردية." });
    }
    setBusy(true);
    try {
      await posApi.closeCashShift(shift.id, value);
      await refresh();
      onChanged();
      setNotice({ kind: "success", message: "تم إغلاق الوردية وتسجيل فرق الصندوق." });
    } catch (error) {
      setNotice({ kind: "error", message: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  return <section className="page-panel operations-panel">
    <div className="panel-heading"><div><span className="workspace-kicker">CASH CONTROL</span><h2>الصندوق والورديات</h2></div><button className="secondary-action" onClick={() => void refresh()} disabled={!enabled || busy}>تحديث</button></div>
    <KeyedNotice notice={notice} />
    {!shift ? <div className="operation-form-card cash-open-form">
      <h3>لا توجد وردية مفتوحة</h3>
      <label>الرصيد الافتتاحي ({currency})<input value={opening} onChange={(e) => setOpening(e.target.value)} inputMode="decimal" disabled={!enabled || busy} /></label>
      <button className="primary-action" onClick={() => void open()} disabled={!enabled || busy}>فتح وردية جديدة</button>
    </div> : <>
      <div className="cash-metrics">
        <article><small>رقم الوردية</small><strong>#{shift.shiftNumber}</strong></article>
        <article><small>الرصيد الافتتاحي</small><strong>{money(shift.openingCash, shift.currency)}</strong></article>
        <article><small>النقد المتوقع</small><strong>{money(shift.expectedCash, shift.currency)}</strong></article>
        <article><small>تاريخ الفتح</small><strong>{new Date(shift.openedAt).toLocaleString("ar-PS")}</strong></article>
      </div>
      <div className="operation-forms">
        <div className="operation-form-card">
          <h3>إضافة حركة نقدية</h3>
          <label>نوع الحركة<select value={movementType} onChange={(e) => setMovementType(e.target.value as "CASH_IN" | "CASH_OUT")}><option value="CASH_IN">إيداع نقدي</option><option value="CASH_OUT">سحب نقدي</option></select></label>
          <label>المبلغ<input value={movementAmount} onChange={(e) => setMovementAmount(e.target.value)} inputMode="decimal" /></label>
          <label>الوصف<input value={movementDescription} onChange={(e) => setMovementDescription(e.target.value)} /></label>
          <button className="secondary-action form-submit" onClick={() => void addMovement()} disabled={!enabled || busy}>تسجيل الحركة</button>
        </div>
        <div className="operation-form-card">
          <h3>إغلاق الوردية</h3>
          <label>النقد المعدود فعليًا ({shift.currency})<input value={counted} onChange={(e) => setCounted(e.target.value)} inputMode="decimal" placeholder={String(shift.expectedCash)} /></label>
          <p className="form-hint">سيحسب النظام فرق الصندوق بين النقد المتوقع والمعدود، ويحفظه ضمن سجل الوردية.</p>
          <button className="primary-action" onClick={() => void close()} disabled={!enabled || busy}>إغلاق الوردية</button>
        </div>
      </div>
      <div className="panel-subheading"><h3>دفتر حركات الصندوق</h3><span>{movements.length} حركة</span></div>
      <div className="table-scroll"><table><thead><tr><th>التاريخ</th><th>الحركة</th><th>الوصف</th><th>الاتجاه</th><th>المبلغ</th></tr></thead><tbody>
        {movements.map((movement) => <tr key={movement.id}><td>{new Date(movement.createdAt).toLocaleString("ar-PS")}</td><td>{movement.movementType}</td><td>{movement.description || "—"}</td><td>{movement.direction === "IN" ? "داخل" : "خارج"}</td><td className={movement.direction === "IN" ? "money-in" : "money-out"}>{movement.direction === "IN" ? "+" : "−"}{money(movement.amount, shift.currency)}</td></tr>)}
        {!movements.length && <tr><td colSpan={5} className="empty-table">لا توجد حركات بعد.</td></tr>}
      </tbody></table></div>
    </>}
  </section>;
}
