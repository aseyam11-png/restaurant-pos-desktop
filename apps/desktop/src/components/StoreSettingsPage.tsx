import { useEffect, useState, type FormEvent } from "react";
import { posApi, type StoreSettings } from "../lib/posApi";

type Props = {
  enabled: boolean;
  settings: StoreSettings | null;
  onSaved: (settings: StoreSettings) => void;
};

const currencies = [
  ["ILS", "شيكل إسرائيلي"],
  ["SAR", "ريال سعودي"],
  ["AED", "درهم إماراتي"],
  ["USD", "دولار أمريكي"],
  ["EUR", "يورو"],
  ["JOD", "دينار أردني"],
] as const;

export function StoreSettingsPage({ enabled, settings, onSaved }: Props) {
  const [currency, setCurrency] = useState(settings?.currency || "ILS");
  const [taxRate, setTaxRate] = useState(String(settings?.taxRatePercent ?? 0));
  const [businessName, setBusinessName] = useState(settings?.businessName || "Restaurant");
  const [receiptFooter, setReceiptFooter] = useState(settings?.receiptFooter || "");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ kind: "success" | "error"; message: string } | null>(null);

  useEffect(() => {
    if (!settings) return;
    setCurrency(settings.currency);
    setTaxRate(String(settings.taxRatePercent));
    setBusinessName(settings.businessName);
    setReceiptFooter(settings.receiptFooter);
  }, [settings]);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const rate = Number(taxRate);
    if (!Number.isFinite(rate) || rate < 0 || rate > 100 || !/^\d{1,3}(\.\d{1,2})?$/.test(taxRate.trim())) {
      setNotice({ kind: "error", message: "نسبة الضريبة يجب أن تكون بين 0 و100 وبحد أقصى منزلتين عشريتين." });
      return;
    }
    if (businessName.trim().length < 2) {
      setNotice({ kind: "error", message: "اسم المنشأة يجب أن يتكون من حرفين على الأقل." });
      return;
    }
    setBusy(true);
    try {
      const saved = await posApi.updateStoreSettings({
        currency,
        taxRatePercent: rate,
        businessName: businessName.trim(),
        receiptFooter: receiptFooter.trim(),
      });
      onSaved(saved);
      setNotice({ kind: "success", message: "تم حفظ إعدادات المنشأة." });
    } catch (error) {
      setNotice({
        kind: "error",
        message: error instanceof Error ? error.message : "تعذر حفظ إعدادات المنشأة.",
      });
    } finally {
      setBusy(false);
    }
  };

  return <section className="page-panel operations-panel settings-page">
    <div className="panel-heading">
      <div><span className="workspace-kicker">STORE CONFIGURATION</span><h2>إعدادات المنشأة</h2></div>
      <span className="state-chip">صلاحية المالك</span>
    </div>
    {notice && <div className={`inline-notice ${notice.kind}`} role="status">{notice.message}</div>}
    <form className="operation-form-card" onSubmit={(event) => void submit(event)}>
      <h3>الهوية التجارية والعملة</h3>
      <div className="form-row">
        <label>اسم المنشأة
          <input value={businessName} onChange={(event) => setBusinessName(event.target.value)} minLength={2} maxLength={120} required disabled={!enabled || busy} />
        </label>
        <label>عملة المتجر
          <select value={currency} onChange={(event) => setCurrency(event.target.value)} disabled={!enabled || busy}>
            {currencies.map(([code, name]) => <option key={code} value={code}>{code} · {name}</option>)}
          </select>
        </label>
      </div>
      <div className="settings-currency-warning">
        تغيير العملة بعد وجود عمليات مالية ممنوع لحماية التقارير والأرصدة التاريخية. استخدم العملة الصحيحة قبل أول عملية.
      </div>
      <h3>الضرائب والفاتورة</h3>
      <div className="form-row">
        <label>نسبة الضريبة (%)
          <input value={taxRate} onChange={(event) => setTaxRate(event.target.value)} inputMode="decimal" disabled={!enabled || busy} />
        </label>
        <label>النص أسفل الفاتورة
          <input value={receiptFooter} onChange={(event) => setReceiptFooter(event.target.value)} maxLength={300} disabled={!enabled || busy} />
        </label>
      </div>
      <p className="form-hint">تُحسب الضريبة على قيمة الطلب بعد الخصم داخل الخادم وفق هذه النسبة، مع التقريب إلى منزلتين عشريتين. لا تعتمد الواجهة قيمة الضريبة المرسلة من العميل.</p>
      <button className="primary-action settings-submit" type="submit" disabled={!enabled || busy}>
        {busy ? "جارٍ الحفظ…" : "حفظ الإعدادات"}
      </button>
    </form>
  </section>;
}
