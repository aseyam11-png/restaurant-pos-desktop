import { useEffect, useState, type FormEvent } from "react";
import { posApi, type Category, type Product, type PricingMode } from "../lib/posApi";

type Props = { enabled: boolean; currency: string; onChanged: () => void };

export function CatalogAdminPage({ enabled, currency, onChanged }: Props) {
  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [categoryName, setCategoryName] = useState("");
  const [productCategory, setProductCategory] = useState("");
  const [productName, setProductName] = useState("");
  const [unit, setUnit] = useState("piece");
  const [pricingMode, setPricingMode] = useState<PricingMode>("FIXED");
  const [price, setPrice] = useState("");
  const [stockTracked, setStockTracked] = useState(true);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ kind: "success" | "error"; message: string } | null>(null);

  const refresh = async () => {
    const [categoryRows, productRows] = await Promise.all([posApi.categories(), posApi.products()]);
    setCategories(categoryRows);
    setProducts(productRows);
    if (!productCategory && categoryRows.length) setProductCategory(categoryRows[0].id);
  };
  useEffect(() => {
    if (enabled) void refresh().catch((error) => setNotice({ kind: "error", message: String(error) }));
  }, [enabled]);

  const addCategory = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!categoryName.trim()) return;
    setBusy(true);
    try {
      const row = await posApi.createCategory({ name: categoryName.trim(), sortOrder: categories.length });
      setCategoryName("");
      await refresh();
      setProductCategory(row.id);
      onChanged();
      setNotice({ kind: "success", message: "تم إنشاء التصنيف." });
    } catch (error) {
      setNotice({ kind: "error", message: error instanceof Error ? error.message : "تعذر إنشاء التصنيف." });
    } finally { setBusy(false); }
  };

  const addProduct = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!productCategory || !productName.trim() || !unit.trim()) {
      setNotice({ kind: "error", message: "التصنيف واسم المنتج والوحدة مطلوبة." });
      return;
    }
    let numericPrice: number | null = null;
    if (pricingMode === "FIXED") {
      numericPrice = Number(price);
      if (!Number.isFinite(numericPrice) || numericPrice <= 0 || !/^\d+(\.\d{1,2})?$/.test(price.trim())) {
        setNotice({ kind: "error", message: "السعر الثابت يجب أن يكون موجبًا وبحد أقصى منزلتين عشريتين." });
        return;
      }
    }
    setBusy(true);
    try {
      await posApi.createProduct({
        categoryId: productCategory,
        name: productName.trim(),
        unit: unit.trim(),
        pricingMode,
        price: numericPrice,
        stockTracked,
      });
      setProductName("");
      setPrice("");
      await refresh();
      onChanged();
      setNotice({ kind: "success", message: "تم إنشاء المنتج ورقم المنتج الداخلي تلقائيًا." });
    } catch (error) {
      setNotice({ kind: "error", message: error instanceof Error ? error.message : "تعذر إنشاء المنتج." });
    } finally { setBusy(false); }
  };

  const deactivate = async (product: Product) => {
    if (!window.confirm(`إيقاف المنتج "${product.name}" عن البيع؟ لن تُحذف الفواتير القديمة.`)) return;
    setBusy(true);
    try {
      await posApi.deactivateProduct(product.id);
      await refresh();
      onChanged();
      setNotice({ kind: "success", message: "تم إيقاف المنتج مع الاحتفاظ بسجلاته التاريخية." });
    } catch (error) {
      setNotice({ kind: "error", message: error instanceof Error ? error.message : "تعذر إيقاف المنتج." });
    } finally { setBusy(false); }
  };

  return <section className="page-panel operations-panel">
    <div className="panel-heading"><div><span className="workspace-kicker">CATALOG CONTROL</span><h2>إدارة المنتجات والتصنيفات</h2></div><button className="secondary-action" onClick={() => void refresh()} disabled={!enabled || busy}>تحديث</button></div>
    {notice && <div className={`inline-notice ${notice.kind}`}>{notice.message}</div>}
    <div className="operation-forms catalog-admin-forms">
      <form className="operation-form-card" onSubmit={(event) => void addCategory(event)}>
        <h3>إضافة تصنيف</h3>
        <label>اسم التصنيف<input value={categoryName} onChange={(event) => setCategoryName(event.target.value)} maxLength={80} required disabled={!enabled || busy} /></label>
        <button className="secondary-action form-submit" type="submit" disabled={!enabled || busy}>إضافة التصنيف</button>
        <div className="supplier-count"><strong>{categories.length}</strong><span>تصنيف نشط</span></div>
      </form>
      <form className="operation-form-card" onSubmit={(event) => void addProduct(event)}>
        <h3>إضافة منتج</h3>
        <label>التصنيف<select value={productCategory} onChange={(event) => setProductCategory(event.target.value)} disabled={!enabled || busy} required>
          <option value="">اختر التصنيف</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
        </select></label>
        <label>اسم المنتج<input value={productName} onChange={(event) => setProductName(event.target.value)} maxLength={120} required disabled={!enabled || busy} /></label>
        <div className="form-row">
          <label>الوحدة<input value={unit} onChange={(event) => setUnit(event.target.value)} maxLength={30} required disabled={!enabled || busy} /></label>
          <label>نظام التسعير<select value={pricingMode} onChange={(event) => setPricingMode(event.target.value as PricingMode)} disabled={!enabled || busy}><option value="FIXED">سعر ثابت</option><option value="ASK_AT_SALE">السعر عند البيع</option></select></label>
        </div>
        {pricingMode === "FIXED" && <label>السعر ({currency})<input value={price} onChange={(event) => setPrice(event.target.value)} inputMode="decimal" required disabled={!enabled || busy} /></label>}
        <label className="inline-checkbox"><input type="checkbox" checked={stockTracked} onChange={(event) => setStockTracked(event.target.checked)} disabled={!enabled || busy} />تتبع مخزون هذا المنتج</label>
        <button className="primary-action" type="submit" disabled={!enabled || busy || !categories.length}>حفظ المنتج</button>
      </form>
    </div>
    <div className="panel-subheading"><h3>الكتالوج</h3><span>{products.length} منتج</span></div>
    <div className="table-scroll"><table><thead><tr><th>الرقم الداخلي</th><th>المنتج</th><th>التصنيف</th><th>الوحدة</th><th>التسعير</th><th>المخزون</th><th>الحالة</th><th>الإجراء</th></tr></thead><tbody>
      {products.map((product) => <tr key={product.id}>
        <td>#{product.productNumber}</td><td>{product.name}</td>
        <td>{categories.find((category) => category.id === product.categoryId)?.name || "—"}</td>
        <td>{product.unit}</td><td>{product.pricingMode === "FIXED" ? product.price : "عند البيع"}</td>
        <td>{product.stockTracked ? "متتبع" : "غير متتبع"}</td>
        <td><span className={product.active ? "state-chip" : "state-chip warning"}>{product.active ? "نشط" : "متوقف"}</span></td>
        <td>{product.active ? <button className="secondary-action" onClick={() => void deactivate(product)} disabled={!enabled || busy}>إيقاف</button> : "—"}</td>
      </tr>)}
      {!products.length && <tr><td colSpan={8} className="empty-table">لا توجد منتجات مسجلة.</td></tr>}
    </tbody></table></div>
  </section>;
}

