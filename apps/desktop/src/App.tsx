import { useState } from "react";

type Locale = "en" | "ar";

const copy = {
  en: {
    eyebrow: "RESTAURANT OPERATIONS",
    title: "Your service workspace",
    subtitle: "A reliable local-first foundation for daily restaurant operations.",
    status: "Foundation build",
    statusDetail: "Cashier transactions are not enabled in this build.",
    local: "Local-first architecture",
    localDetail: "Core sales must remain available without internet.",
    integrity: "Transaction integrity",
    integrityDetail: "Orders, payments and audit events require durable commits.",
    next: "Next engineering milestone",
    nextDetail: "Validate the desktop shell, local service lifecycle and persistence strategy before enabling sales.",
    language: "العربية",
    footer: "Development build · No live sales or financial records",
  },
  ar: {
    eyebrow: "إدارة المطعم",
    title: "مساحة تشغيل المطعم",
    subtitle: "أساس موثوق يعمل محليًا لتشغيل العمليات اليومية للمطعم.",
    status: "نسخة تأسيسية",
    statusDetail: "عمليات البيع غير مفعّلة في هذه النسخة.",
    local: "بنية تعمل محليًا",
    localDetail: "يجب أن تبقى عمليات البيع الأساسية متاحة دون إنترنت.",
    integrity: "سلامة المعاملات",
    integrityDetail: "يجب حفظ الطلبات والمدفوعات وسجل التدقيق بمعاملات دائمة.",
    next: "المحطة الهندسية التالية",
    nextDetail: "التحقق من تطبيق سطح المكتب والخدمة المحلية واستراتيجية التخزين قبل تفعيل البيع.",
    language: "English",
    footer: "نسخة تطوير · لا توجد مبيعات أو سجلات مالية فعلية",
  },
} satisfies Record<Locale, Record<string, string>>;

export default function App() {
  const [locale, setLocale] = useState<Locale>("ar");
  const t = copy[locale];
  const rtl = locale === "ar";

  return (
    <main className="app-shell" dir={rtl ? "rtl" : "ltr"}>
      <header className="topbar">
        <a className="brand" href="#" aria-label="Restaurant POS home">
          <span className="brand-mark">R</span>
          <span>RESTAURANT <b>POS</b></span>
        </a>
        <div className="topbar-actions">
          <span className="environment"><i /> DEVELOPMENT</span>
          <button className="language-button" onClick={() => setLocale(rtl ? "en" : "ar")}>
            {t.language}
          </button>
        </div>
      </header>

      <section className="content">
        <div className="intro">
          <p className="eyebrow">{t.eyebrow}</p>
          <h1>{t.title}</h1>
          <p className="subtitle">{t.subtitle}</p>
        </div>

        <section className="status-panel" aria-labelledby="status-heading">
          <div className="status-icon" aria-hidden="true">01</div>
          <div className="status-copy">
            <span className="status-label">{t.status}</span>
            <h2 id="status-heading">{t.statusDetail}</h2>
          </div>
          <span className="status-pill">PHASE 0</span>
        </section>

        <div className="principles">
          <article className="principle-card">
            <span className="card-index">01 / OFFLINE</span>
            <div className="card-symbol" aria-hidden="true">⌁</div>
            <h3>{t.local}</h3>
            <p>{t.localDetail}</p>
          </article>
          <article className="principle-card">
            <span className="card-index">02 / DATA</span>
            <div className="card-symbol" aria-hidden="true">▤</div>
            <h3>{t.integrity}</h3>
            <p>{t.integrityDetail}</p>
          </article>
        </div>

        <section className="milestone">
          <div>
            <p className="eyebrow">PHASE 0 · ENGINEERING</p>
            <h2>{t.next}</h2>
            <p>{t.nextDetail}</p>
          </div>
          <span className="milestone-arrow" aria-hidden="true">↗</span>
        </section>

        <footer>{t.footer}</footer>
      </section>
    </main>
  );
}