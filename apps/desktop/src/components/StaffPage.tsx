import { useEffect, useState } from "react";
import { posApi, type StaffUser } from "../lib/posApi";

type Props = { enabled: boolean };

export function StaffPage({ enabled }: Props) {
  const [staff, setStaff] = useState<StaffUser[]>([]);
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<"MANAGER" | "CASHIER">("CASHIER");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ kind: "success" | "error"; message: string } | null>(null);

  const refresh = async () => setStaff(await posApi.staff());
  useEffect(() => {
    if (enabled) void refresh().catch((error) => setNotice({ kind: "error", message: String(error) }));
  }, [enabled]);

  const create = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!/^[A-Za-z0-9._-]{3,40}$/.test(username.trim()) || displayName.trim().length < 2 || password.length < 12) {
      setNotice({ kind: "error", message: "تحقق من اسم المستخدم والاسم الظاهر وكلمة المرور (12 حرفًا على الأقل)." });
      return;
    }
    setBusy(true);
    try {
      await posApi.createStaff({
        username: username.trim(),
        displayName: displayName.trim(),
        temporaryPassword: password,
        role,
      });
      setUsername("");
      setDisplayName("");
      setPassword("");
      await refresh();
      setNotice({ kind: "success", message: "تم إنشاء حساب الموظف. سلّمه بيانات الدخول عبر قناة آمنة." });
    } catch (error) {
      setNotice({ kind: "error", message: error instanceof Error ? error.message : "تعذر إنشاء الحساب." });
    } finally {
      setBusy(false);
    }
  };

  const deactivate = async (user: StaffUser) => {
    if (!window.confirm(`تعطيل حساب ${user.displayName}؟ سيتم إبطال جلساته الحالية.`)) return;
    setBusy(true);
    try {
      await posApi.deactivateStaff(user.userId);
      await refresh();
      setNotice({ kind: "success", message: "تم تعطيل الحساب وإبطال جلساته." });
    } catch (error) {
      setNotice({ kind: "error", message: error instanceof Error ? error.message : "تعذر تعطيل الحساب." });
    } finally {
      setBusy(false);
    }
  };

  return <section className="page-panel operations-panel">
    <div className="panel-heading"><div><span className="workspace-kicker">ACCESS CONTROL</span><h2>حسابات الموظفين والصلاحيات</h2></div><button className="secondary-action" onClick={() => void refresh()} disabled={!enabled || busy}>تحديث</button></div>
    {notice && <div className={`inline-notice ${notice.kind}`}>{notice.message}</div>}
    <form className="operation-form-card staff-form" onSubmit={(event) => void create(event)}>
      <h3>إنشاء حساب موظف</h3>
      <div className="form-row three">
        <label>اسم المستخدم<input value={username} onChange={(event) => setUsername(event.target.value)} minLength={3} maxLength={40} required disabled={!enabled || busy} /></label>
        <label>الاسم الظاهر<input value={displayName} onChange={(event) => setDisplayName(event.target.value)} minLength={2} maxLength={80} required disabled={!enabled || busy} /></label>
        <label>الدور<select value={role} onChange={(event) => setRole(event.target.value as "MANAGER" | "CASHIER")} disabled={!enabled || busy}><option value="CASHIER">كاشير</option><option value="MANAGER">مدير</option></select></label>
      </div>
      <label>كلمة مرور مؤقتة (12 حرفًا على الأقل)<input type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} minLength={12} maxLength={128} required disabled={!enabled || busy} /></label>
      <button className="primary-action" type="submit" disabled={!enabled || busy}>{busy ? "جارٍ الحفظ…" : "إنشاء الحساب"}</button>
      <p className="form-hint">لا تُعرض كلمة المرور مرة أخرى ولا تُخزّن كنص صريح. ينبغي تسليمها للموظف عبر قناة آمنة ثم اعتماد تغييرها في إصدار لاحق.</p>
    </form>
    <div className="panel-subheading"><h3>المستخدمون المحليون</h3><span>{staff.length} حساب</span></div>
    <div className="table-scroll"><table><thead><tr><th>الموظف</th><th>اسم المستخدم</th><th>الدور</th><th>الحالة</th><th>الإجراء</th></tr></thead><tbody>
      {staff.map((user) => <tr key={user.userId}>
        <td>{user.displayName}</td><td>{user.username}</td><td>{user.role === "MANAGER" ? "مدير" : "كاشير"}</td>
        <td><span className={user.active ? "state-chip" : "state-chip warning"}>{user.active ? "نشط" : "معطل"}</span></td>
        <td>{user.active ? <button className="secondary-action" onClick={() => void deactivate(user)} disabled={!enabled || busy}>تعطيل</button> : "—"}</td>
      </tr>)}
      {!staff.length && <tr><td colSpan={5} className="empty-table">لا توجد حسابات موظفين إضافية.</td></tr>}
    </tbody></table></div>
  </section>;
}
