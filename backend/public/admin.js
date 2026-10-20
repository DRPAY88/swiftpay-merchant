const tokenKey = "swiftpay-admin-token";
const loginPanel = document.querySelector("#login-panel");
const dashboard = document.querySelector("#dashboard");
const loginMessage = document.querySelector("#login-message");
const dashboardMessage = document.querySelector("#dashboard-message");
const requestCodeForm = document.querySelector("#request-code-form");
const verifyCodeForm = document.querySelector("#verify-code-form");
const signOutButton = document.querySelector("#sign-out");
let registrationSecureStorageAvailable = false;
let adjustmentSummary = null;

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
  if (users.length === 0) {
    const row = document.createElement("tr");
    const message = cell(row, "No users found.");
    message.colSpan = 4;
    message.className = "empty-state";
    tbody.append(row);
    return;
  }
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

async function loadOnboarding() {
  const toggle = document.querySelector("#onboarding-enabled");
  const status = document.querySelector("#onboarding-status");
  toggle.disabled = true;
  const setting = await api("/v1/admin/onboarding");
  toggle.checked = setting.enabled;
  status.textContent = setting.enabled
    ? "Onboarding is enabled for eligible new merchant accounts."
    : "Onboarding is disabled for eligible new merchant accounts.";
  toggle.disabled = false;
}

async function updateOnboarding(enabled) {
  const toggle = document.querySelector("#onboarding-enabled");
  const status = document.querySelector("#onboarding-status");
  toggle.disabled = true;
  try {
    const setting = await api("/v1/admin/onboarding", {
      method: "PATCH",
      body: JSON.stringify({ enabled })
    });
    toggle.checked = setting.enabled;
    status.textContent = setting.enabled
      ? "Onboarding is enabled for eligible new merchant accounts."
      : "Onboarding is disabled for eligible new merchant accounts.";
  } catch (error) {
    toggle.checked = !enabled;
    showMessage(dashboardMessage, error.message);
    return;
  } finally {
    toggle.disabled = false;
  }
  try {
    await loadAudit();
    showMessage(dashboardMessage, "Onboarding setting updated.", false);
  } catch (error) {
    showMessage(dashboardMessage, `Onboarding setting saved, but the audit trail could not refresh: ${error.message}`);
  }
}

async function loadRegistrationSettings() {
  const toggle = document.querySelector("#registration-enabled");
  const status = document.querySelector("#registration-status");
  toggle.disabled = true;
  try {
    const setting = await api("/v1/admin/registration-settings");
    registrationSecureStorageAvailable = setting.secureStorageAvailable;
    toggle.checked = setting.enabled;
    status.textContent = !registrationSecureStorageAvailable
      ? "Registration is unavailable. Configure KYB_ENCRYPTION_KEY before accepting applications."
      : setting.enabled
        ? "Open registration is accepting applications."
        : "Open registration is disabled.";
  } catch (error) {
    status.textContent = `Could not load registration settings: ${error.message}`;
    throw error;
  } finally {
    toggle.disabled = !registrationSecureStorageAvailable;
  }
}

async function updateRegistrationSetting(enabled) {
  const toggle = document.querySelector("#registration-enabled");
  const status = document.querySelector("#registration-status");
  toggle.disabled = true;
  try {
    const setting = await api("/v1/admin/registration-settings", {
      method: "PATCH",
      body: JSON.stringify({ enabled })
    });
    registrationSecureStorageAvailable = setting.secureStorageAvailable;
    toggle.checked = setting.enabled;
    status.textContent = !registrationSecureStorageAvailable
      ? "Registration is unavailable. Configure KYB_ENCRYPTION_KEY before accepting applications."
      : setting.enabled
        ? "Open registration is accepting applications."
        : "Open registration is disabled.";
    await Promise.all([loadRegistrations(), loadAudit()]);
    showMessage(dashboardMessage, "Registration setting updated.", false);
  } catch (error) {
    toggle.checked = !enabled;
    showMessage(dashboardMessage, error.message);
  } finally {
    toggle.disabled = !registrationSecureStorageAvailable;
  }
}

async function loadRegistrations() {
  const registrations = await api("/v1/admin/registrations");
  const tbody = document.querySelector("#registrations-table");
  tbody.replaceChildren();
  if (registrations.length === 0) {
    const row = document.createElement("tr");
    const message = cell(row, "No registration applications found.");
    message.colSpan = 4;
    message.className = "empty-state";
    tbody.append(row);
    return;
  }
  for (const registration of registrations) {
    const row = document.createElement("tr");
    cell(row, registration.email);
    cell(row, registration.status);
    cell(row, new Date(registration.updatedAt).toLocaleString());
    const actions = document.createElement("td");
    const review = document.createElement("button");
    review.className = "button secondary";
    review.textContent = "View";
    review.addEventListener("click", () => showRegistration(registration.email));
    actions.append(review);
    row.append(actions);
    tbody.append(row);
  }
}

async function downloadRegistrationDocument(email, kind) {
  const response = await fetch(`/v1/admin/registrations/${encodeURIComponent(email)}/documents/${encodeURIComponent(kind)}`, {
    headers: { Authorization: `Bearer ${sessionStorage.getItem(tokenKey)}` }
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    if (response.status === 401) signOut();
    throw new Error(body.error || `Document download failed (${response.status})`);
  }
  const blob = await response.blob();
  const extension = blob.type === "application/pdf" ? "pdf" : blob.type === "image/png" ? "png" : "jpg";
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${kind}.${extension}`;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function showRegistration(email) {
  const detail = document.querySelector("#registration-detail");
  detail.replaceChildren();
  detail.textContent = "Loading application details…";
  detail.classList.remove("hidden");
  try {
    const registration = await api(`/v1/admin/registrations/${encodeURIComponent(email)}`);
    detail.replaceChildren();
    detail.classList.remove("hidden");
    const heading = document.createElement("h3");
    heading.textContent = `Application: ${registration.email} (${registration.status})`;
    detail.append(heading);
    const fields = [
      ["Business name", registration.profile.businessName],
      ["Registration number", registration.profile.businessRegistrationNumber],
      ["Business address", registration.profile.businessAddress],
      ["Business type", registration.profile.businessType],
      ["Authorized representative", registration.profile.representativeFullName],
      ["Representative phone", registration.profile.representativePhone],
      ["Review note", registration.reviewNote]
    ];
    const list = document.createElement("dl");
    for (const [label, value] of fields) {
      const term = document.createElement("dt");
      term.textContent = label;
      const description = document.createElement("dd");
      description.textContent = value || "—";
      list.append(term, description);
    }
    detail.append(list);
    const documents = document.createElement("div");
    documents.className = "review-actions";
    for (const documentInfo of registration.documents) {
      const download = document.createElement("button");
      download.type = "button";
      download.className = "button secondary";
      download.textContent = `Download ${documentInfo.kind.replaceAll("_", " ")}`;
      download.addEventListener("click", () => downloadRegistrationDocument(email, documentInfo.kind)
        .catch((error) => showMessage(dashboardMessage, error.message)));
      documents.append(download);
    }
    detail.append(documents);

    const actions = document.createElement("div");
    actions.className = "review-actions";
    if (registration.status === "submitted") {
      const note = document.createElement("textarea");
      note.maxLength = 500;
      note.placeholder = "Review note (optional; visible to applicant)";
      actions.append(note);
      for (const status of ["approved", "rejected"]) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `button ${status === "rejected" ? "danger" : ""}`;
        button.textContent = status === "approved" ? "Approve and activate" : "Reject";
        button.addEventListener("click", () => reviewRegistration(email, status, note.value));
        actions.append(button);
      }
    } else if (registration.status === "approved" || registration.status === "rejected") {
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "button danger";
      remove.textContent = "Delete application and documents";
      remove.addEventListener("click", () => deleteRegistration(email));
      actions.append(remove);
    }
    detail.append(actions);
  } catch (error) {
    detail.textContent = `Could not load application details: ${error.message}`;
    showMessage(dashboardMessage, error.message);
  }
}

async function reviewRegistration(email, status, note) {
  const prompt = status === "approved"
    ? `Approve ${email} and activate merchant sign-in?`
    : `Reject ${email}'s application? Documents will remain stored until explicitly deleted.`;
  if (!window.confirm(prompt)) return;
  try {
    await api(`/v1/admin/registrations/${encodeURIComponent(email)}`, {
      method: "PATCH",
      body: JSON.stringify({ status, note })
    });
    await Promise.all([loadRegistrations(), loadUsers(), loadAudit()]);
    await showRegistration(email);
    showMessage(dashboardMessage, `Application ${status}.`, false);
  } catch (error) {
    showMessage(dashboardMessage, error.message);
  }
}

async function deleteRegistration(email) {
  if (!window.confirm(`Permanently delete ${email}'s application and all encrypted KYB documents? This cannot be undone.`)) return;
  try {
    await api(`/v1/admin/registrations/${encodeURIComponent(email)}`, { method: "DELETE" });
    document.querySelector("#registration-detail").classList.add("hidden");
    await Promise.all([loadRegistrations(), loadAudit()]);
    showMessage(dashboardMessage, "Application and documents permanently deleted.", false);
  } catch (error) {
    showMessage(dashboardMessage, error.message);
  }
}

async function updateUser(email, status) {
  const confirmation = status === "removed"
    ? `Remove merchant access for ${email}?`
    : status === "suspended"
      ? `Suspend ${email}'s merchant access?`
      : null;
  if (confirmation && !window.confirm(confirmation)) return;
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
    `₱ ${Number(adjustment.outstanding).toLocaleString(undefined, { minimumFractionDigits: 2 })}`;
  adjustmentSummary = adjustment;
  renderAdjustmentSettlement(adjustment);
  renderAdjustments(adjustment.adjustments);
  await loadManualDeposits();
}

function renderAdjustmentSettlement(adjustment) {
  const outstanding = Number(adjustment.outstanding);
  const latestPayment = adjustment.latestPayment;
  document.querySelector("#adjustment-outstanding").textContent =
    `₱ ${outstanding.toLocaleString(undefined, { minimumFractionDigits: 2 })} remains`;
  const status = document.querySelector("#adjustment-payment-status");
  const createButton = document.querySelector("#create-adjustment-qr");
  const checkButton = document.querySelector("#check-adjustment-payment");
  const quoteLine = document.querySelector("#adjustment-payment-quote");
  const qrContainer = document.querySelector("#adjustment-qr");
  const qrImage = document.querySelector("#adjustment-qr-image");
  const qrPayload = document.querySelector("#adjustment-qr-payload");
  const qrReference = document.querySelector("#adjustment-qr-reference");
  const activePayment = latestPayment && ["CREATING", "PENDING", "UNKNOWN"].includes(latestPayment.status);

  createButton.disabled = !(outstanding > 0) || Boolean(activePayment);
  if (!latestPayment) {
    status.textContent = outstanding > 0
      ? "A ₩500,000 KRW payment must be confirmed before this adjustment can be transferred."
      : "No positive adjustment balance is awaiting reconciliation.";
  } else if (latestPayment.status === "EXECUTED") {
    status.textContent = outstanding <= 0
      ? "Payment verified; no positive adjustment balance remains to transfer."
      : adjustment.canTransfer
        ? `Payment verified. ₱ ${Number(latestPayment.settledAmountPhp).toLocaleString(undefined, { minimumFractionDigits: 2 })} was applied to the adjustment.`
        : "A newer positive adjustment was recorded; create a new QR payment before transferring it.";
  } else if (activePayment) {
    status.textContent = latestPayment.status === "UNKNOWN"
      ? `The provider outcome is unknown. Do not create another QR; contact support with reference ${latestPayment.referenceNo}.`
      : `Payment status: ${latestPayment.status}. Complete the QR payment, then check its status.`;
  } else {
    status.textContent = `Last QR payment status: ${latestPayment.status}. A new QR can be created if an adjustment remains.`;
  }
  quoteLine.textContent = latestPayment
    ? `Locked QR amount: ₩ ${Number(latestPayment.customerAmountKrw).toLocaleString()} → PHP ${Number(latestPayment.expectedAmountPhp).toLocaleString(undefined, { minimumFractionDigits: 2 })}. Up to PHP ${Number(latestPayment.offsetAmountPhp).toLocaleString(undefined, { minimumFractionDigits: 2 })} is applied to the adjustment; any excess remains in the shared provider balance.`
    : "";

  checkButton.classList.toggle("hidden", !latestPayment?.paymentId || !activePayment);
  const hasQr = Boolean(latestPayment?.qrImageBase64 || latestPayment?.qrCode) && activePayment;
  qrContainer.classList.toggle("hidden", !hasQr);
  qrImage.classList.toggle("hidden", !latestPayment?.qrImageBase64 || !activePayment);
  qrImage.removeAttribute("src");
  if (latestPayment?.qrImageBase64 && activePayment) {
    qrImage.src = `data:image/png;base64,${latestPayment.qrImageBase64}`;
  }
  qrPayload.classList.toggle("hidden", !latestPayment?.qrCode || Boolean(latestPayment?.qrImageBase64) || !activePayment);
  qrPayload.textContent = latestPayment?.qrCode || "";
  qrReference.textContent = hasQr ? `Payment reference: ${latestPayment.referenceNo}` : "";

  const adjustmentTransferToggle = document.querySelector("#transfer-adjustment");
  adjustmentTransferToggle.disabled = !adjustment.canTransfer;
  if (!adjustment.canTransfer) adjustmentTransferToggle.checked = false;
  if (adjustmentTransferToggle.checked && adjustment.canTransfer) {
    document.querySelector("#transfer-amount").value = Math.min(outstanding, 50_000).toFixed(2);
  }
}

async function createAdjustmentKrwQr() {
  const button = document.querySelector("#create-adjustment-qr");
  button.disabled = true;
  try {
    const quote = await api("/v1/payments/qrph/quote", {
      method: "POST",
      body: JSON.stringify({ amountKrw: 500_000 })
    });
    await api("/v1/payments/qrph", {
      method: "POST",
      body: JSON.stringify({
        quoteToken: quote.quoteToken,
        adjustmentSettlement: true
      })
    });
    await Promise.all([loadBalance(), loadAudit()]);
    showMessage(dashboardMessage, "₩500,000 KRW QR created. The adjustment changes only after provider verification.", false);
  } catch (error) {
    showMessage(dashboardMessage, error.message);
    await loadBalance().catch((loadError) => showMessage(dashboardMessage, loadError.message));
  } finally {
    if (adjustmentSummary) renderAdjustmentSettlement(adjustmentSummary);
    else button.disabled = false;
  }
}

async function checkAdjustmentKrwPayment() {
  const paymentId = adjustmentSummary?.latestPayment?.paymentId;
  if (!paymentId) return;
  const button = document.querySelector("#check-adjustment-payment");
  button.disabled = true;
  try {
    const result = await api(
      `/v1/admin/balance-adjustments/krw-payments/${encodeURIComponent(paymentId)}/status`
    );
    await Promise.all([loadBalance(), loadAudit()]);
    showMessage(
      dashboardMessage,
      result.status === "EXECUTED"
        ? "KRW payment verified; its PHP settlement has been applied to the adjustment."
        : `Payment is not settled yet (${result.status}).`,
      result.status !== "EXECUTED"
    );
  } catch (error) {
    showMessage(dashboardMessage, error.message);
  } finally {
    button.disabled = false;
  }
}

async function loadManualDeposits() {
  const deposits = await api("/v1/wallet/manual-deposits?limit=100");
  const tbody = document.querySelector("#manual-deposits-table");
  tbody.replaceChildren();
  for (const deposit of deposits) {
    const row = document.createElement("tr");
    cell(row, `₱ ${Number(deposit.amount).toLocaleString(undefined, { minimumFractionDigits: 2 })}`);
    cell(row, deposit.bankName);
    cell(row, deposit.referenceNo);
    cell(row, new Date(deposit.createdAt).toLocaleString());
    tbody.append(row);
  }
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
  const results = await Promise.allSettled([
    loadUsers(), loadOnboarding(), loadRegistrationSettings(), loadRegistrations(),
    loadBalance(), loadTransfers(), loadAudit()
  ]);
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

document.querySelector("#onboarding-enabled").addEventListener("change", (event) => {
  updateOnboarding(event.currentTarget.checked);
});
document.querySelector("#registration-enabled").addEventListener("change", (event) => {
  updateRegistrationSetting(event.currentTarget.checked);
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
  if (!window.confirm("Record an internal-only adjustment? This does not change funds held by the payment provider.")) return;
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

document.querySelector("#manual-deposit-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const amount = Number(document.querySelector("#manual-deposit-amount").value);
  const bankName = document.querySelector("#manual-deposit-bank").value.trim();
  const referenceNo = document.querySelector("#manual-deposit-reference").value.trim();
  const notes = document.querySelector("#manual-deposit-notes").value.trim();
  if (!window.confirm("Confirm that this PHP bank deposit has been verified before recording it?")) return;
  try {
    await api("/v1/admin/manual-bank-deposits", {
      method: "POST",
      body: JSON.stringify({ amount, bankName, referenceNo, notes })
    });
    event.currentTarget.reset();
    await Promise.all([loadManualDeposits(), loadAudit()]);
    showMessage(dashboardMessage, "Verified manual bank deposit recorded in wallet history.", false);
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
  const adjustmentTransfer = document.querySelector("#transfer-adjustment").checked;
  const transfer = {
    amount,
    referenceNo: `ADMIN-${crypto.randomUUID()}`,
    channel: "INSTAPAY",
    institutionCode,
    recipientInformation: { accountNumber, fullName },
    ...(adjustmentTransfer ? { adjustmentTransfer: true } : {}),
    ...(remarks ? { remarks } : {})
  };
  const transferPurpose = adjustmentTransfer ? " against the remaining adjusted balance" : "";
  if (!window.confirm(`Send a REAL PHP ${amount.toFixed(2)} InstaPay transfer${transferPurpose} to ${fullName}, account ending ${accountNumber.slice(-4)}?`)) return;
  try {
    const result = await api("/v1/disbursements", { method: "POST", body: JSON.stringify(transfer) });
    event.currentTarget.reset();
    await Promise.all([loadTransfers(), loadAudit(), loadBalance()]);
    showMessage(dashboardMessage, `Transfer accepted as pending (${result.id}). Verify its final status with the payment provider.`, false);
  } catch (error) {
    showMessage(dashboardMessage, error.message);
  }
});

document.querySelector("#create-adjustment-qr").addEventListener("click", createAdjustmentKrwQr);
document.querySelector("#check-adjustment-payment").addEventListener("click", checkAdjustmentKrwPayment);
document.querySelector("#transfer-adjustment").addEventListener("change", (event) => {
  if (!event.currentTarget.checked) return;
  if (!adjustmentSummary?.canTransfer) {
    event.currentTarget.checked = false;
    showMessage(dashboardMessage, "Verify the ₩500,000 KRW QR payment before transferring an adjustment.");
    return;
  }
  const outstanding = Number(adjustmentSummary.outstanding);
  if (outstanding <= 0) {
    event.currentTarget.checked = false;
    showMessage(dashboardMessage, "No positive adjustment balance remains to transfer.");
    return;
  }
  document.querySelector("#transfer-amount").value = Math.min(outstanding, 50_000).toFixed(2);
});

document.querySelector("#refresh-balance").addEventListener("click", () => loadBalance().catch((error) => showMessage(dashboardMessage, error.message)));
document.querySelector("#refresh-transfers").addEventListener("click", () => loadTransfers().catch((error) => showMessage(dashboardMessage, error.message)));
signOutButton.addEventListener("click", signOut);

if (sessionStorage.getItem(tokenKey)) {
  loadDashboard().catch(signOut);
}
