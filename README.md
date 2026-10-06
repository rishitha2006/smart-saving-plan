# Smart Saving Plan — Smart Personal Finance Manager

Smart Saving Plan is a Spring Boot + Thymeleaf + MongoDB personal finance application.

## Storage behavior

- User accounts, financial profiles, incomes and expenses are stored in MongoDB.
- Smart Saving Plan does not use MySQL, H2, SQLite, JSON files, or another local database for financial data.
- CSV/Excel imports are processed during the request and their transaction records are written to MongoDB; the uploaded file is not saved as application data.
- Smart Saving Plan pings MongoDB during startup and intentionally fails to start when MongoDB is unavailable. There is no local-database fallback.
- MongoDB itself stores its database files on the MongoDB server's configured storage. If MongoDB runs locally, that storage is managed by MongoDB, not by Smart Saving Plan.

## Calculation changes

1. During setup, the total of all individual expense categories is calculated.
2. If that category total is higher than the overall monthly spending entered by the user, the higher category total is saved as monthly spending.
3. Current savings is kept separate and is never treated as an expense.
4. Recorded transactions are compared with planned monthly spending instead of being blindly added to it. The higher value is used, preventing double-counting.
5. The dashboard shows safe spending per day and per week after the planned monthly saving is protected.
6. The corrected calculation is used across dashboard, analytics and projections.

## Run

1. Install Java 21, Maven and MongoDB.
2. Start MongoDB.
3. Copy `.env.example` to `.env` and set `MONGODB_URI`, `MONGODB_DATABASE`, and `SERVER_PORT` for your environment. The `.env` file is ignored by Git and should never be committed.
4. From this project folder run:

```bash
mvn spring-boot:run
```

5. Open the configured server address (for example `http://localhost:8080`).

If MongoDB is not reachable, Smart Saving Plan intentionally stops instead of starting with local financial storage.

## Stack

- Java 21
- Spring Boot 3.5.5
- Spring MVC + Thymeleaf
- Spring Data MongoDB
- BCrypt password hashing
- Chart.js
- Apache POI for Excel import


## Financial calculation rules

- MongoDB is the only application data store. There is no local JSON/file database fallback.
- Registration preserves the user's entered overall monthly spending and separately stores the category total.
- Effective planned spending is `max(overall estimate, category total)`.
- Recorded expenses are compared with the effective plan; they are not added to it, preventing double counting.
- If recorded expenses exceed the plan, recorded expenses become the effective current-month expense figure.
- Savings are protected separately from expenses.
- Safe daily spending = `(income - effective expenses - protected savings) / remaining days`.
- Safe weekly spending = up to 7 daily allowances, capped by the remaining spendable amount.
- If expenses exceed income or savings protection consumes the remaining amount, safe additional spending is shown as ₹0 rather than a misleading positive number.
- Passwords are BCrypt-hashed.
- The application performs a MongoDB `ping` during startup and fails startup when MongoDB is unavailable.


## Expense import and notifications
- Notifications are available from the Notifications button and are generated from the user's spending/budget data.
- CSV/XLSX imports support headers `Amount`, `Date`, `Description`, and optional `Category`. Common aliases such as `Debit`, `Transaction Date`, `Merchant`, and `Narration` are also recognized.
- Uploaded transaction files are processed during the request and their records are stored in MongoDB; sample upload files are not bundled with this release.
- The dashboard includes a Spending Risk Factor from 0–100 based on spending as a percentage of income. The visual scale progresses from dark green to green, yellow, orange and red as spending risk increases.

## React frontend

A React/Vite frontend is included in `frontend/`. It preserves the original `app.css` exactly and uses the existing Spring Boot/MongoDB backend. See `frontend/README.md` for setup and run instructions.

---

## React frontend

A React frontend is included under `frontend/`. The original CSS and Spring backend are preserved. See `frontend/README.md` for the exact run commands.
