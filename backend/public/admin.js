const tokenKey = "swiftpay-admin-token";
const loginPanel = document.querySelector("#login-panel");
const dashboard = document.querySelector("#dashboard");
const loginMessage = document.querySelector("#login-message");
const dashboardMessage = document.querySelector("#dashboard-message");
const requestCodeForm = document.querySelector("#request-code-form");
const verifyCodeForm = document.querySelector("#verify-code-form");
const signOutButton = document.querySelector("#sign-out");

function showMessage(target, message, error = true) {
  target.textContent = message;
  target.style.color = error ? "#a42d2d" : "#176346";
}

async function api(path, options = {}) {
  const token = sessionStorage.getItem(tokenKey);
  const response = await fetch(path, {
    ...options,
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    if (response.status === 401 && !path.startsWith("/v1/auth/")) signOut();
    throw new Error(body.error || `Request failed (${response.status})`);
  }
  return body;
}

function signOut() {
  sessionStorage.removeItem(tokenKey);
  dashboard.classList.add("hidden");
  loginPanel.classList.remove("hidden");
  signOutButton.classList.add("hidden");
  verifyCodeForm.classList.add("hidden");
}

function cell(row, value) {
  const td = document.createElement("td");
  td.textContent = value ?? "";
  row.append(td);
  return td;
}

async function loadUsers() {
  const users = await api("/v1/admin/users");
  const tbody = document.querySelector("#users-table");
  tbody.replaceChildren();
  for (const user of users) {
    const row = document.createElement("tr");
    cell(row, user.email);
    cell(row, user.role === "super_admin" ? "Super admin" : user.status);
    cell(row, new Date(user.created_at).toLocaleString());
    const actions = document.createElement("td");
    if (user.role === "merchant") {
      const toggle = document.createElement("button");
      toggle.className = "button secondary";
      toggle.textContent = user.status === "active" ? "Suspend" : "Reactivate";
      toggle.addEventListener("click", () => updateUser(user.email, user.status === "active" ? "suspended" : "active"));
      actions.append(toggle);
      const remove = document.createElement("button");
      remove.className = "button danger";
      remove.textContent = "Remove";
      remove.disabled = user.status === "removed";
      remove.addEventListener("click", () => updateUser(user.email, "removed"));
      actions.append(remove);
    }
    row.append(actions);
    tbody.append(row);
  }
}

async function updateUser(email, status) {
  try {
    await api(`/v1/admin/users/${encodeURIComponent(email)}`, {
      method: "PATCH",
      body: JSON.stringify({ status })
    });
    await Promise.all([loadUsers(), loadAudit()]);
    showMessage(dashboardMessage, `Updated ${email}: ${status}`, false);
  } catch (error) {
    showMessage(dashboardMessage, error.message);
  }
}

async function loadBalance() {
  const [wallet, adjustment] = await Promise.all([
    api("/v1/wallet/balance"),
    api("/v1/admin/balance-adjustments")
  ]);
  document.querySelector("#provider-balance").textContent =
    `₱ ${Number(wallet.balance).toLocaleString(undefined, { minimumFractionDigits: 2 })}`;
  document.querySelector("#account-number").textContent =
    wallet.accountNumber ? `Funding account: ${wallet.accountNumber}` : "";
  document.querySelector("#adjustment-total").textContent =
    `₱ ${Number(adjustment.total).toLocaleString(undefined, { minimumFractionDigits: 2 })}`;
  renderAdjustments(adjustment.adjustments);
}

function renderAdjustments(items) {
  const tbody = document.querySelector("#adjustments-table");
  tbody.replaceChildren();
  for (const item of items) {
    const row = document.createElement("tr");
    cell(row, `₱ ${Number(item.amount).toLocaleString(undefined, { minimumFractionDigits: 2 })}`);
    cell(row, item.reason);
    cell(row, item.actor);
    cell(row, new Date(item.created_at).toLocaleString());
    tbody.append(row);
  }
}

async function loadTransfers() {
  const data = await api("/v1/disbursements");
  document.querySelector("#transfer-data").textContent = JSON.stringify(data, null, 2);
}

async function loadAudit() {
  const items = await api("/v1/admin/audit?limit=100");
  const tbody = document.querySelector("#audit-table");
  tbody.replaceChildren();
  for (const item of items) {
    const row = document.createElement("tr");
    cell(row, item.action);
    cell(row, item.target);
    cell(row, item.actor);
    cell(row, new Date(item.created_at).toLocaleString());
    tbody.append(row);
  }
}

async function loadDashboard() {
  await api("/v1/admin/me");
  loginPanel.classList.add("hidden");
  dashboard.classList.remove("hidden");
  signOutButton.classList.remove("hidden");
  const results = await Promise.allSettled([loadUsers(), loadBalance(), loadTransfers(), loadAudit()]);
  const failed = results.find((result) => result.status === "rejected");
  if (failed) showMessage(dashboardMessage, failed.reason.message);
}

requestCodeForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const email = document.querySelector("#admin-email").value.trim();
  try {
    await api("/v1/auth/request-otp", {
      method: "POST",
      body: JSON.stringify({ email })
    });
    verifyCodeForm.classList.remove("hidden");
    showMessage(loginMessage, "If this address is enabled for admin access, a sign-in code has been sent.", false);
  } catch (error) {
    showMessage(loginMessage, error.message);
  }
});

verifyCodeForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const email = document.querySelector("#admin-email").value.trim();
  const code = document.querySelector("#admin-code").value.trim();
  try {
    const session = await api("/v1/auth/verify-otp", {
      method: "POST",
      body: JSON.stringify({ email, code })
    });
    sessionStorage.setItem(tokenKey, session.accessToken);
    await loadDashboard();
  } catch (error) {
    showMessage(loginMessage, error.message);
  }
});

document.querySelector("#add-user-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const emailInput = document.querySelector("#new-user-email");
  try {
    await api("/v1/admin/users", { method: "POST", body: JSON.stringify({ email: emailInput.value.trim() }) });
    emailInput.value = "";
    await Promise.all([loadUsers(), loadAudit()]);
    showMessage(dashboardMessage, "Merchant account added/reactivated.", false);
  } catch (error) {
    showMessage(dashboardMessage, error.message);
  }
});

document.querySelector("#adjustment-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const amount = Number(document.querySelector("#adjustment-amount").value);
  const reason = document.querySelector("#adjustment-reason").value.trim();
  if (!window.confirm("Record an internal-only adjustment? This does not change real SwiftPay funds.")) return;
  try {
    await api("/v1/admin/balance-adjustments", {
      method: "POST",
      body: JSON.stringify({ amount, reason })
    });
    event.currentTarget.reset();
    await Promise.all([loadBalance(), loadAudit()]);
    showMessage(dashboardMessage, "Internal adjustment recorded.", false);
  } catch (error) {
    showMessage(dashboardMessage, error.message);
  }
});

document.querySelector("#transfer-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const amount = Number(document.querySelector("#transfer-amount").value);
  const institutionCode = document.querySelector("#transfer-bank").value.trim();
  const accountNumber = document.querySelector("#transfer-account").value.trim();
  const fullName = document.querySelector("#transfer-name").value.trim();
  const remarks = document.querySelector("#transfer-remarks").value.trim();
  const transfer = {
    amount,
    referenceNo: `ADMIN-${crypto.randomUUID()}`,
    channel: "INSTAPAY",
    institutionCode,
    recipientInformation: { accountNumber, fullName },
    ...(remarks ? { remarks } : {})
  };
  if (!window.confirm(`Send a REAL PHP ${amount.toFixed(2)} InstaPay transfer to ${fullName}, account ending ${accountNumber.slice(-4)}?`)) return;
  try {
    const result = await api("/v1/disbursements", { method: "POST", body: JSON.stringify(transfer) });
    event.currentTarget.reset();
    await Promise.all([loadTransfers(), loadAudit()]);
    showMessage(dashboardMessage, `Transfer accepted as pending (${result.id}). Verify its final status with SwiftPay.`, false);
  } catch (error) {
    showMessage(dashboardMessage, error.message);
  }
});

document.querySelector("#refresh-balance").addEventListener("click", () => loadBalance().catch((error) => showMessage(dashboardMessage, error.message)));
document.querySelector("#refresh-transfers").addEventListener("click", () => loadTransfers().catch((error) => showMessage(dashboardMessage, error.message)));
signOutButton.addEventListener("click", signOut);

if (sessionStorage.getItem(tokenKey)) {
  loadDashboard().catch(signOut);
}
