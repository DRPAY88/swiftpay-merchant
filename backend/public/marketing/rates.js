(() => {
  const status = document.getElementById("market-rates-status");
  const amountInput = document.getElementById("market-rates-amount");
  if (!status || !amountInput) return;

  const phpRate = document.getElementById("market-rates-php-per-krw");
  const usdtRate = document.getElementById("market-rates-usdt-per-krw");
  const phpEstimate = document.getElementById("market-rates-php-estimate");
  const usdtEstimate = document.getElementById("market-rates-usdt-estimate");
  const updated = document.getElementById("market-rates-updated");
  let rates = null;
  let isStale = false;
  let isFallbackRates = false;

  function setStatus(message, state) {
    status.textContent = message;
    status.dataset.state = state;
  }

  function formatRate(value, currency) {
    const maximumFractionDigits = currency === "PHP" ? 6 : 8;
    return new Intl.NumberFormat("en-PH", { maximumFractionDigits }).format(value);
  }

  function formatEstimate(value, currency) {
    return `${currency} ${new Intl.NumberFormat("en-PH", {
      minimumFractionDigits: 2,
      maximumFractionDigits: currency === "PHP" ? 2 : 4
    }).format(value)}`;
  }

  function formatTime(value) {
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: "medium",
      timeStyle: "short"
    }).format(new Date(value));
  }

  function renderEstimates() {
    const amount = Number(amountInput.value);
    if (!rates || !Number.isFinite(amount) || amount <= 0) {
      phpEstimate.textContent = "—";
      usdtEstimate.textContent = "—";
      if (rates) {
        setStatus(
          isStale
            ? "Showing last available rates"
            : isFallbackRates
              ? "Daily reference rates"
              : "Enter a positive KRW amount",
          isStale ? "stale" : "input"
        );
      }
      return;
    }
    phpEstimate.textContent = formatEstimate(amount * rates.PHP.perKrw, "PHP");
    usdtEstimate.textContent = rates.USDT
      ? `≈ ${formatEstimate(amount * rates.USDT.perKrw, "USDT")}`
      : "USDT rate unavailable";
    setStatus(
      isStale
        ? "Showing last available rates"
        : isFallbackRates
          ? "Indicative rates · daily reference source"
          : rates.USDT
            ? "Indicative rates"
            : "PHP estimate only",
      isStale ? "stale" : rates.USDT ? "live" : "partial"
    );
  }

  async function refreshRates() {
    if (!rates) setStatus("Updating rates…", "loading");
    try {
      const response = await fetch("/api/marketing/rates", {
        headers: { Accept: "application/json" },
        cache: "no-store"
      });
      if (!response.ok) throw new Error("Rate request failed");
      const result = await response.json();
      if (
        !Number.isFinite(result?.rates?.PHP?.perKrw) ||
        result.rates.PHP.perKrw <= 0
      ) {
        throw new Error("Rate response was invalid");
      }
      if (
        result.rates.USDT &&
        (!Number.isFinite(result.rates.USDT.perKrw) || result.rates.USDT.perKrw <= 0)
      ) {
        throw new Error("USDT rate response was invalid");
      }

      rates = result.rates;
      isStale = false;
      isFallbackRates = result.source === "Frankfurter";
      const sourceLabel = result.source === "Frankfurter"
        ? "Frankfurter daily reference"
        : "CurrencyAPI";
      phpRate.textContent = `₩1 = ${formatRate(rates.PHP.perKrw, "PHP")} PHP`;
      usdtRate.textContent = rates.USDT
        ? `₩1 ≈ ${formatRate(rates.USDT.perKrw, "USDT")} USDT`
        : "USDT rate unavailable";
      updated.textContent = `Source: ${sourceLabel} · Last checked ${formatTime(result.retrievedAt)} · KRW/PHP data: ${formatTime(rates.PHP.updatedAt)}${
        rates.USDT
          ? ` · USD FX data: ${formatTime(rates.USDT.usdFxUpdatedAt)} · USDT/USD data: ${formatTime(rates.USDT.usdtUsdUpdatedAt)}`
          : ""
      }`;
      status.dataset.state = "";
      renderEstimates();
    } catch {
      if (rates) {
        isStale = true;
        setStatus("Showing last available rates", "stale");
        if (!updated.textContent.includes("Refresh failed;")) {
          updated.textContent += " · Refresh failed; these values may be outdated.";
        }
      } else {
        phpRate.textContent = "Rate unavailable";
        usdtRate.textContent = "Rate unavailable";
        updated.textContent = "";
        setStatus("Rates unavailable", "error");
      }
    }
  }

  amountInput.addEventListener("input", renderEstimates);
  refreshRates();
  window.setInterval(refreshRates, 60_000);
})();
