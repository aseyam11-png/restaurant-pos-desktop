import { useState, type FormEvent } from "react";
import { authApi, setSessionToken, type AuthSession } from "../lib/posApi";

type Props = {
  session: AuthSession;
  onChanged: (session: AuthSession) => void;
};

export function ChangePasswordGate({ session, onChanged }: Props) {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    if (newPassword.length < 12 || newPassword.length > 128) {
      setError("كلمة المرور الجديدة يجب أن تكون بين 12 و128 حرفًا.");
      return;
    }
    if (newPassword !== confirmation) {
      setError("تأكيد كلمة المرور غير مطابق.");
      return;
    }
    setBusy(true);
    try {
      const updated = await authApi.changePassword({ currentPassword, newPassword });
      setSessionToken(updated.accessToken);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmation("");
      onChanged(updated);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "تعذر تغيير كلمة المرور.");
    } finally {
      setBusy(false);
    }
  };

  return <main className="auth-screen" dir="rtl">
    <section className="auth-card">
      <div className="auth-brand">
        <span className="auth-mark">R</span>
        <div><strong>RESTAURANT POS</strong><small>{session.displayName} · {session.role}</small></div>
      </div>
      <span className="workspace-kicker">ACCOUNT SECURITY</span>
      <h1>غيّر كلمة المرور المؤقتة</h1>
      <p className="auth-description">يجب تغيير كلمة المرور المؤقتة قبل استخدام النظام. بعد التغيير ستُبطل الجلسات السابقة ويُصدر النظام جلسة جديدة.</p>
      {error && <div className="auth-error" role="alert">{error}</div>}
      <form onSubmit={(event) => void submit(event)}>
        <label>كلمة المرور الحالية
          <input type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} minLength={12} maxLength={128} required />
        </label>
        <label>كلمة المرور الجديدة
          <input type="password" autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} minLength={12} maxLength={128} required />
        </label>
        <label>تأكيد كلمة المرور الجديدة
          <input type="password" autoComplete="new-password" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} minLength={12} maxLength={128} required />
        </label>
        <button className="primary-action auth-submit" type="submit" disabled={busy || !currentPassword || !newPassword || !confirmation}>
          {busy ? "جارٍ تغيير كلمة المرور…" : "حفظ كلمة المرور الجديدة"}
        </button>
      </form>
      <footer>New password is stored as a one-way BCrypt hash</footer>
    </section>
  </main>;
}
