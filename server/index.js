const express = require('express')
const bodyParser = require('body-parser')
const cors = require('cors')
const rateLimit = require('express-rate-limit')
const helmet = require('helmet')
const fs = require('fs')
const path = require('path')

const app = express()
if (process.env.NODE_ENV === 'production') {
  throw new Error('The mock approval server is for local development only and cannot run in production')
}
app.use(helmet())
app.use(cors())
app.use(bodyParser.json())

const limiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 500,
  standardHeaders: true,
  legacyHeaders: false,
})
app.use(limiter)

const DATA_FILE = path.join(__dirname, 'approvals.json')
const LEDGER_FILE = path.join(__dirname, 'ledger.json')
const MERCHANTS_FILE = path.join(__dirname, 'merchants.json')

function loadJson(file, defaultVal) {
  try {
    const raw = fs.readFileSync(file, 'utf8')
    return JSON.parse(raw)
  } catch (e) {
    return defaultVal
  }
}

function saveJson(file, data) {
  fs.writeFileSync(file, JSON.stringify(data, null, 2))
}

let db = loadJson(DATA_FILE, { approvals: [] })
let ledgerDb = loadJson(LEDGER_FILE, { transactions: [] })
let merchantsDb = loadJson(MERCHANTS_FILE, {
  merchants: {
    "5312": {
      merchantId: "5312",
      businessName: "SwiftPay Enterprise Merchant",
      email: "merchant@swiftpay.ph",
      phone: "+639171234567",
      kycStatus: "APPROVED",
      accountStatus: "ACTIVE",
      monthlyVolume: 1250000.0,
      apiKey: process.env.SWIFTPAY_ACCESS_KEY || "",
      balance: 12500.0
    }
  }
})

// --- Persistence Helpers ---

async function saveApprovalEntry(entry) {
  db.approvals = db.approvals.filter(a => a.requestId !== entry.requestId)
  db.approvals.push(entry)
  saveJson(DATA_FILE, db)
  return entry
}

async function findApprovalById(id) {
  return db.approvals.find(a => a.requestId === id) || null
}

async function listPendingApprovalsByEmail(email) {
  return db.approvals.filter(a => a.email === email && a.status === 'PENDING' && Date.now() < a.expiresAt)
}

async function hasExistingDeviceForEmail(email) {
  return db.approvals.some(a => a.email === email && Date.now() < a.expiresAt && (a.status === 'PENDING' || a.status === 'APPROVED'))
}

async function updateApprovalStatus(id, status) {
  const entry = db.approvals.find(a => a.requestId === id)
  if (!entry) return null
  entry.status = status
  saveJson(DATA_FILE, db)
  return entry
}

// --- Ledger Helpers ---

async function recordLedgerTransaction(tx) {
  ledgerDb.transactions = ledgerDb.transactions.filter(t => t.transactionId !== tx.transactionId)
  ledgerDb.transactions.unshift(tx)
  saveJson(LEDGER_FILE, ledgerDb)
  return tx
}

async function getLedgerTransactions(merchantId) {
  return ledgerDb.transactions.filter(t => !merchantId || t.merchantId === merchantId)
}

async function getLedgerBalance(merchantId) {
  const txs = await getLedgerTransactions(merchantId)
  return txs
    .filter(t => (t.status || '').toUpperCase() === 'SUCCESS' || (t.status || '').toUpperCase() === 'APPROVED' || (t.status || '').toUpperCase() === 'CAPTURED')
    .reduce((acc, t) => acc + (parseFloat(t.amount) || 0), 0)
}

// --- API Endpoints ---

// 1. Authentication & OTP
app.post('/v1/auth/otp/send', async (req, res) => {
  const { email, phone, code } = req.body
  console.log(`Sending OTP ${code || '123456'} to ${email || phone}`)
  res.json({ success: true, message: 'OTP sent successfully' })
})

app.post('/v1/auth/otp/verify', async (req, res) => {
  const { email, phone, code } = req.body
  res.json({ success: true, token: 'sw_live_token_' + Math.random().toString(36).slice(2) })
})

app.get('/v1/merchant/profile', async (req, res) => {
  const merchantId = req.headers['x-merchant-id'] || '5312'
  const merchant = merchantsDb.merchants[merchantId] || merchantsDb.merchants['5312']
  res.json(merchant)
})

// 2. Orders & Checkout (SwiftPay Gateway API)
app.post('/v1/swiftpay/create-order', async (req, res) => {
  const { amount, reference_no, description, currency } = req.body
  const orderId = 'ORD-' + Math.random().toString(36).slice(2, 10).toUpperCase()

  const tx = {
    transactionId: orderId,
    merchantId: '5312',
    amount: parseFloat(amount) || 0,
    currency: currency || 'PHP',
    status: 'SUCCESS',
    type: 'CHECKOUT_ORDER',
    createdAt: Date.now()
  }
  await recordLedgerTransaction(tx)

  res.json({
    id: orderId,
    orderId: orderId,
    status: 'SUCCESS',
    checkoutUrl: `https://pay.swiftpay.ph/checkout/${orderId}`,
    paymentUrl: `https://pay.swiftpay.ph/checkout/${orderId}`,
    amount: tx.amount,
    currency: tx.currency,
    referenceNo: reference_no || orderId
  })
})

app.get('/v1/swiftpay/status/:identifier', async (req, res) => {
  const id = req.params.identifier
  res.json({
    id: id,
    orderId: id,
    status: 'SUCCESS',
    paymentStatus: 'PAYMENT_SUCCESS',
    amount: 1000.0,
    currency: 'PHP',
    referenceNo: id
  })
})

app.post('/v1/payments/create', async (req, res) => {
  const { amount, reference_no, description, currency } = req.body
  const payId = 'PAY-' + Math.random().toString(36).slice(2, 10).toUpperCase()

  const tx = {
    transactionId: payId,
    merchantId: '5312',
    amount: parseFloat(amount) || 0,
    currency: currency || 'PHP',
    status: 'SUCCESS',
    type: 'PAYMENT',
    createdAt: Date.now()
  }
  await recordLedgerTransaction(tx)

  res.json({
    id: payId,
    status: 'SUCCESS',
    amount: tx.amount,
    currency: tx.currency,
    referenceNo: reference_no || payId
  })
})

app.get('/v1/payments/:payment_id', async (req, res) => {
  const id = req.params.payment_id
  res.json({
    id: id,
    status: 'SUCCESS',
    paymentStatus: 'PAYMENT_SUCCESS',
    amount: 1000.0,
    currency: 'PHP'
  })
})

// 3. Dynamic QR & Payment Links
app.post('/v1/magpie/qr/dynamic', async (req, res) => {
  const { amount, reference_no } = req.body
  const qid = 'QR-' + Math.random().toString(36).slice(2, 10).toUpperCase()
  res.json({
    paymentId: qid,
    qrCode: `00020101021228690016PH.CO.SWIFTPAY0110${qid}5204000053036085802PH5913SwiftPay Merchant6006Laguna62110707${qid}`,
    amount: amount,
    status: 'SUCCESS'
  })
})

app.post('/v1/xend/create-qr-code', async (req, res) => {
  const { amount } = req.body
  const qid = 'XQR-' + Math.random().toString(36).slice(2, 10).toUpperCase()
  res.json({
    paymentId: qid,
    qrCode: `00020101021228690016PH.CO.SWIFTPAY0110${qid}5204000053036085802PH5913SwiftPay Merchant6006Laguna62110707${qid}`,
    amount: amount,
    status: 'SUCCESS'
  })
})

app.post('/v1/xend/create-payment-link', async (req, res) => {
  const { amount, description, referenceNo } = req.body
  const lid = 'LINK-' + Math.random().toString(36).slice(2, 10).toUpperCase()
  res.json({
    id: lid,
    paymentLinkUrl: `https://pay.swiftpay.ph/link/${lid}`,
    status: 'ACTIVE',
    amount: amount || 0,
    description: description
  })
})

// 4. Disbursements & Institutions
app.post('/v1/swiftpay/disbursements/send', async (req, res) => {
  const { amount, bankCode, accountNumber, firstName, lastName, referenceNo } = req.body
  const did = 'DISB-' + Math.random().toString(36).slice(2, 10).toUpperCase()

  const tx = {
    transactionId: did,
    merchantId: '5312',
    amount: -(parseFloat(amount) || 0),
    currency: 'PHP',
    status: 'SUCCESS',
    type: 'DISBURSEMENT',
    createdAt: Date.now()
  }
  await recordLedgerTransaction(tx)

  res.json({
    id: did,
    disbursementId: did,
    status: 'SUCCESS',
    amount: amount,
    currency: 'PHP',
    referenceNo: referenceNo || did
  })
})

app.get('/v1/swiftpay/disbursements', async (req, res) => {
  const txs = await getLedgerTransactions('5312')
  res.json(txs.filter(t => t.type === 'DISBURSEMENT'))
})

app.get('/v1/swiftpay/institutions', async (req, res) => {
  res.json([
    { code: 'GCASH', name: 'GCash', type: 'E-WALLET', status: 'ACTIVE' },
    { code: 'MAYA', name: 'Maya Wallet', type: 'E-WALLET', status: 'ACTIVE' },
    { code: 'GRABPAY', name: 'GrabPay', type: 'E-WALLET', status: 'ACTIVE' },
    { code: 'BDO', name: 'BDO Unibank', type: 'BANK', status: 'ACTIVE' },
    { code: 'BPI', name: 'Bank of the Philippine Islands', type: 'BANK', status: 'ACTIVE' },
    { code: 'UNIONBANK', name: 'UnionBank', type: 'BANK', status: 'ACTIVE' },
    { code: 'METROBANK', name: 'Metrobank', type: 'BANK', status: 'ACTIVE' }
  ])
})

// 5. Wallet & Ledger
app.get('/v1/wallet/balance', async (req, res) => {
  const balance = await getLedgerBalance('5312')
  res.json({ balance, currency: 'PHP', availableBalance: balance })
})

app.get('/v1/wallet/transactions', async (req, res) => {
  const txs = await getLedgerTransactions('5312')
  res.json(txs.map(t => ({
    id: t.transactionId,
    amount: t.amount,
    status: t.status,
    createdAt: new Date(t.createdAt).toISOString()
  })))
})

app.post('/v1/ledger/record', async (req, res) => {
  const { transactionId, merchantId, amount, currency, status, type, createdAt } = req.body
  const tx = {
    transactionId: transactionId || 'TX-' + Math.random().toString(36).slice(2, 10).toUpperCase(),
    merchantId: merchantId || '5312',
    amount: parseFloat(amount) || 0,
    currency: currency || 'PHP',
    status: status || 'SUCCESS',
    type: type || 'PAYMENT',
    createdAt: createdAt || Date.now()
  }
  await recordLedgerTransaction(tx)
  res.json({ success: true, transaction: tx })
})

// 6. Device Login Approvals
app.post('/approvals', async (req, res) => {
  const { requestId, email, deviceId, deviceName } = req.body
  if (!email) return res.status(400).json({ error: 'email required' })

  const isFirstDevice = !(await hasExistingDeviceForEmail(email))
  const status = isFirstDevice ? 'APPROVED' : 'PENDING'

  const id = requestId || Math.random().toString(36).slice(2)
  const now = Date.now()
  const expiresAt = now + (5 * 60 * 1000)
  const entry = { requestId: id, email, deviceId, deviceName, status, createdAt: now, expiresAt }

  await saveApprovalEntry(entry)
  res.json(entry)
})

app.get('/approvals/:id', async (req, res) => {
  const id = req.params.id
  const entry = await findApprovalById(id)
  if (!entry) return res.status(404).json({ error: 'not found' })
  res.json(entry)
})

app.get('/approvals', async (req, res) => {
  const email = req.query.email
  const pending = await listPendingApprovalsByEmail(email)
  res.json(pending)
})

app.post('/approvals/:id/approve', async (req, res) => {
  const id = req.params.id
  const updated = await updateApprovalStatus(id, 'APPROVED')
  if (!updated) return res.status(404).json({ error: 'not found' })
  res.json(updated)
})

app.post('/approvals/:id/deny', async (req, res) => {
  const id = req.params.id
  const updated = await updateApprovalStatus(id, 'DENIED')
  if (!updated) return res.status(404).json({ error: 'not found' })
  res.json(updated)
})

const PORT = process.env.PORT || 3000
app.listen(PORT, '127.0.0.1', () => {
  console.log('Local mock approval server listening on port', PORT)
})
