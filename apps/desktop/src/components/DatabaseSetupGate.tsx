import { useEffect, useState, type FormEvent } from "react";
import {
  configureDatabase,
  type ApiStatus,
  type DatabaseConfigStatus,
} from "../lib/posApi";

type Props = {
  current: DatabaseConfigStatus | null;
  api: ApiStatus;
  onConfigured: (status: DatabaseConfigStatus) => void;
};

export function DatabaseSetupGate({ current, api, onConfigured }: Props) {
  const [host, setHost] = useState(current?.host || "127.0.0.1");
  const [port, setPort] = useState(String(current?.port || 5432));
  const [database, setDatabase] = useState(current?.database || "restaurant_pos");
  const [username, setUsername] = useState(current?.username || "restaurant_pos");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!current) return;
    setHost(current.host || "127.0.0.1");
    setPort(String(current.port || 5432));
    setDatabase(current.database || "restaurant_pos");
    setUsername(current.username || "restaurant_pos");
  }, [current]);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    const numericPort = Number(port);
    if (!["localhost", "127.0.0.1"].includes(host.trim())) {
      setError("في هذا الإصدار يجب أن تكون قاعدة البيانات على نفس الجهاز (localhost).");
      return;
    }
    if (!Number.isInteger(numericPort) || numericPort < 1 || numericPort > 65535) {
      setError("رقم منفذ PostgreSQL غير صالح.");
      return;
    }
    if (!/^[A-Za-z][A-Za-z0-9_]{0,62}$/.test(database.trim())
      || !/^[A-Za-z][A-Za-z0-9_]{0,62}$/.test(username.trim())) {
      setError("اسم قاعدة البيانات والمستخدم يجب أن يكونا معرفين صالحين.");
      return;
    }
    if (!password || password.length > 256) {
      setError("أدخل كلمة مرور مستخدم PostgreSQL.");
      return;
    }

    setBusy(true);
    try {
      const saved = await configureDatabase({
        host: host.trim(),
        port: numericPort,
        database: database.trim(),
        username: username.trim(),
        password,
      });
      setPassword("");
      onConfigured(saved);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusy(false);
    }
  };

  return <main className="database-setup-screen" dir="rtl">
    <section className="database-setup-card">
      <div className="auth-brand">
        <span className="auth-mark">R</span>
        <div><strong>RESTAURANT POS</strong><small>إعداد قاعدة البيانات المحلية</small></div>
      </div>
      <span className="workspace-kicker">FIRST-RUN DATABASE SETUP</span>
      <h1>{current?.configured ? "تعذر الاتصال بقاعدة البيانات" : "إعداد قاعدة بيانات المطعم"}</h1>
      <p className="auth-description">
        يعتمد البرنامج على PostgreSQL محلي لحفظ المبيعات والمخزون والحسابات. يجب تثبيت PostgreSQL وتشغيله وإنشاء قاعدة البيانات والمستخدم قبل المتابعة.
      </p>
      <div className="database-prerequisite">
        <strong>المتطلبات قبل الحفظ</strong>
        <ol>
          <li>تثبيت PostgreSQL على هذا الكمبيوتر وتشغيل الخدمة.</li>
          <li>إنشاء قاعدة بيانات ومستخدم تطبيق بصلاحياته المحددة باستخدام دليل التثبيت.</li>
          <li>إدخال بيانات اتصال المستخدم أدناه. سيحفظ التطبيق كلمة المرور في خزنة نظام التشغيل.</li>
        </ol>
        <a href="https://github.com/aseyam11-png/restaurant-pos-desktop/blob/phase-0/engineering-baseline/docs/operations/local-database-setup.md" target="_blank" rel="noreferrer">فتح دليل إعداد PostgreSQL</a>
      </div>
      {current?.configured && <div className="auth-error">بيانات اتصال محفوظة: {current.host}:{current.port}/{current.database}. حالة الخدمة: {api.message}</div>}
      {error && <div className="auth-error" role="alert">{error}</div>}
      <form onSubmit={(event) => void submit(event)}>
        <div className="form-row">
          <label>عنوان الخادم
            <input value={host} onChange={(event) => setHost(event.target.value)} required disabled={busy} />
          </label>
          <label>المنفذ
            <input value={port} onChange={(event) => setPort(event.target.value)} inputMode="numeric" required disabled={busy} />
          </label>
        </div>
        <label>اسم قاعدة البيانات
          <input value={database} onChange={(event) => setDatabase(event.target.value)} required disabled={busy} />
        </label>
        <label>مستخدم التطبيق
          <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required disabled={busy} />
        </label>
        <label>كلمة مرور قاعدة البيانات
          <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" required disabled={busy} />
        </label>
        <button className="primary-action auth-submit" type="submit" disabled={busy}>
          {busy ? "جارٍ حفظ الاتصال وتشغيل الخدمة…" : "حفظ الاتصال وبدء النظام"}
        </button>
      </form>
      <footer>Database password is stored in Windows Credential Manager or macOS Keychain; it is not written to the application configuration file.</footer>
    </section>
  </main>;
}
