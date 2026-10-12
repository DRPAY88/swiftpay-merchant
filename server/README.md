This is a local-only mock server for development and testing. Its payment, wallet, and disbursement endpoints return fabricated data; it does not contact SwiftPay and must never be used for real payments.

Run locally with Node.js:

```sh
npm install
npm start
```

The server listens on `127.0.0.1:3000` by default. Set `PORT` to use a different port. It refuses to start when `NODE_ENV=production`.

The production API is in `backend/`; deploy that service instead.
