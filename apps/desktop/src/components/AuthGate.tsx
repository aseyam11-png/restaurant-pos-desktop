import { useEffect, useState, type FormEvent } from "react";
import { authApi, setSessionToken, type AuthSession, type SetupStatus } from "../lib/posApi";

type Props = {
  enabled: boolean;
  onAuthenticated: (session: AuthSession) => void;
};

export function AuthGate({ enabled, onAuthenticated }: Props) {
  const [setup, setSetup] = useState<SetupStatus | null>(null);
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let current = true;
    if (!enabled) {
      setSetup(null);
      setError("");
      return () => { current = false; };
    }
    setLoading(true);
    authApi.setupStatus()
      .then((value) => { if (current) setSetup(value); })
      .catch((reason) => { if (current) setError(reason instanceof Error ? reason.message : "تعذر التحقق من إعداد الحساب."); })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [enabled]);

  const acceptSession = (session: AuthSession) => {
    setSessionToken(session.accessToken);
    setPassword("");
    setConfirmation("");
    onAuthenticated(session);
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    if (!/^[A-Za-z0-9._-]{3,40}$/.test(username.trim())) {
      setError("اسم المستخدم يجب أن يكون من 3 إلى 40 حرفًا أو رقمًا.");
      return;
    }
    if (password.length < 12 || password.length > 128) {
      setError("كلمة المرور يجب أن تكون بين 12 و128 حرفًا.");
      return;
    }
    if (setup && !setup.initialized) {
      if (displayName.trim().length < 2) {
        setError("أدخل اسمًا ظاهرًا صحيحًا.");
        return;
      }
      if (password !== confirmation) {
        setError("تأكيد كلمة المرور غير مطابق.");
        return;
      }
    }

    setBusy(true);
    try {
      const session = setup?.initialized
        ? await authApi.login({ username: username.trim(), password })
        : await authApi.bootstrap({ username: username.trim(), displayName: displayName.trim(), password });
      acceptSession(session);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "تعذر تسجيل الدخول.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="auth-screen" dir="rtl">
      <section className="auth-card">
        <div className="auth-brand">
          <span className="auth-mark">R</span>
          <div><strong>RESTAURANT POS</strong><small>إدارة المطعم · تسجيل دخول محلي</small></div>
        </div>
        <span className="workspace-kicker">{setup?.initialized ? "SECURE LOCAL ACCESS" : "FIRST RUN SETUP"}</span>
        <h1>{loading ? "جارٍ التحقق…" : setup?.initialized ? "تسجيل الدخول" : "إعداد حساب المالك"}</h1>
        <p className="auth-description">
          {setup?.initialized
            ? "أدخل بيانات حساب الموظف للمتابعة. تنتهي الجلسة تلقائيًا بعد 12 ساعة."
            : "أنشئ حساب المالك الأول. لا يوجد حساب افتراضي أو كلمة مرور ثابتة داخل البرنامج."}
        </p>
        {!enabled && <div className="auth-error">الخدمة المحلية غير متاحة. لن يتم قبول بيانات الدخول قبل اتصال قاعدة البيانات.</div>}
        {error && <div className="auth-error" role="alert">{error}</div>}
        <form onSubmit={(event) => void submit(event)}>
          <label>اسم المستخدم
            <input autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} required minLength={3} maxLength={40} disabled={!enabled || loading || busy} />
          </label>
          {!setup?.initialized && <>
            <label>الاسم الظاهر
              <input autoComplete="name" value={displayName} onChange={(event) => setDisplayName(event.target.value)} required minLength={2} maxLength={80} disabled={!enabled || loading || busy} />
            </label>
          </>}
          <label>كلمة المرور
            <input type="password" autoComplete={setup?.initialized ? "current-password" : "new-password"} value={password} onChange={(event) => setPassword(event.target.value)} required minLength={12} maxLength={128} disabled={!enabled || loading || busy} />
          </label>
          {!setup?.initialized && <>
            <label>تأكيد كلمة المرور
              <input type="password" autoComplete="new-password" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} required minLength={12} maxLength={128} disabled={!enabled || loading || busy} />
            </label>
            <p className="auth-hint">استخدم كلمة مرور قوية لا تقل عن 12 حرفًا. سيتم حفظ تجزئتها المشفرة فقط.</p>
          </>}
          <button className="primary-action auth-submit" type="submit" disabled={!enabled || loading || busy || !setup}>
            {busy ? "جارٍ التحقق…" : setup?.initialized ? "دخول آمن" : "إنشاء حساب المالك"}
          </button>
        </form>
        <footer>Local authentication · Session token stored in memory only</footer>
      </section>
    </main>
  );
}
