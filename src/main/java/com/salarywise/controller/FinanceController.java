package com.salarywise.controller;

import com.salarywise.model.*;
import com.salarywise.repository.*;
import jakarta.servlet.http.HttpSession;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class FinanceController {

 @Autowired
 UserRepository users;

 @Autowired
 FinancialProfileRepository profiles;

 @Autowired
 ExpenseRepository expenses;

 @Autowired
 IncomeRepository incomes;

 private User current(HttpSession s) {
  return (User) s.getAttribute("loggedInUser");
 }

 @GetMapping("/onboarding")
 public String onboarding(HttpSession s, Model m) {
  User u = current(s);
  if (u == null) return "redirect:/login";
  if (u.isOnboardingComplete()) return "redirect:/dashboard";
  m.addAttribute("user", u);
  return "onboarding";
 }

 @PostMapping("/onboarding")
 public String saveOnboarding(
         @RequestParam double salary,
         @RequestParam double otherIncome,
         @RequestParam int salaryDate,
         @RequestParam double monthlySpending,
         @RequestParam double currentSavings,
         @RequestParam double savingsTarget,
         @RequestParam(required = false, defaultValue = "") String goalName,
         @RequestParam double goalAmount,
         @RequestParam double grocery,
         @RequestParam double housing,
         @RequestParam double travel,
         @RequestParam double food,
         @RequestParam double shopping,
         @RequestParam double utilities,
         @RequestParam double education,
         @RequestParam double healthcare,
         @RequestParam double entertainment,
         @RequestParam double other,
         @RequestParam double salaryGrowthRate,
         @RequestParam double investmentReturnRate,
         HttpSession s,
         Model m) {

  User u = current(s);

  if (u == null) return "redirect:/login";

  // Treat money inputs as finite, non-negative values. This prevents invalid
  // numeric values from silently becoming 0.00 later in the dashboard/plan.
  if (!isValidMoney(salary) || !isValidMoney(otherIncome)
          || !isValidMoney(monthlySpending) || !isValidMoney(currentSavings)
          || !isValidMoney(savingsTarget) || !isValidMoney(goalAmount)
          || !isValidMoney(grocery) || !isValidMoney(housing)
          || !isValidMoney(travel) || !isValidMoney(food)
          || !isValidMoney(shopping) || !isValidMoney(utilities)
          || !isValidMoney(education) || !isValidMoney(healthcare)
          || !isValidMoney(entertainment) || !isValidMoney(other)) {
   m.addAttribute("user", u);
   m.addAttribute("error", "Please enter valid non-negative amounts.");
   return "onboarding";
  }

  u.setMonthlySalary(cleanMoney(salary));
  u.setMonthlyOtherIncome(cleanMoney(otherIncome));
  u.setSalaryDate(Math.min(31, Math.max(1, salaryDate)));

  // The user's overall expense is preserved exactly as entered. Category
  // expenses are used only when their total is higher, so the plan can never
  // underestimate spending.
  double categoryTotal = categorySum(
          grocery, housing, travel, food, shopping, utilities,
          education, healthcare, entertainment, other
  );
  double enteredSpending = cleanMoney(monthlySpending);
  double correctedMonthlySpending = Math.max(enteredSpending, categoryTotal);
  // Keep both values: the user's overall estimate is preserved for transparency,
  // while the effective plan never underestimates the category breakdown.
  u.setEnteredMonthlySpending(enteredSpending);
  u.setCategoryExpenseTotal(categoryTotal);
  u.setMonthlySpending(correctedMonthlySpending);

  u.setCurrentSavings(cleanMoney(currentSavings));
  u.setSavingsTarget(cleanMoney(savingsTarget));
  u.setGoalName(goalName.trim());
  u.setGoalAmount(cleanMoney(goalAmount));
  u.setSalaryGrowthRate(cleanMoney(salaryGrowthRate));
  u.setInvestmentReturnRate(cleanMoney(investmentReturnRate));
  u.setOnboardingComplete(true);
  u.setUpdatedAt(LocalDateTime.now());

  users.save(u);

  FinancialProfile p = profiles.findById(u.getId())
          .orElse(new FinancialProfile());

  p.setUserId(u.getId());
  p.setGrocery(cleanMoney(grocery));
  p.setHousing(cleanMoney(housing));
  p.setTravel(cleanMoney(travel));
  p.setFood(cleanMoney(food));
  p.setShopping(cleanMoney(shopping));
  p.setUtilities(cleanMoney(utilities));
  p.setEducation(cleanMoney(education));
  p.setHealthcare(cleanMoney(healthcare));
  p.setEntertainment(cleanMoney(entertainment));
  p.setOther(cleanMoney(other));

  // Use the category estimates entered during account creation as the
  // initial Budget & Goals limits. These are limits, not actual spending.
  // Keep the same category mapping used by the live budget engine.
  p.setBudgetFood(cleanMoney(food));
  p.setBudgetGrocery(cleanMoney(grocery));
  p.setBudgetHousing(cleanMoney(housing));
  p.setBudgetTransport(cleanMoney(travel));
  p.setBudgetShopping(cleanMoney(shopping));
  p.setBudgetUtilities(cleanMoney(utilities));
  p.setBudgetEntertainment(cleanMoney(entertainment));
  p.setBudgetHealthcare(cleanMoney(healthcare));
  p.setBudgetEducation(cleanMoney(education));
  p.setBudgetOther(cleanMoney(other));
  p.setBudgetBills(0.0);

  profiles.save(p);

  s.setAttribute("loggedInUser", u);

  return "redirect:/dashboard";
 }

 private boolean isValidMoney(double value) {
  return Double.isFinite(value) && value >= 0;
 }

 private double cleanMoney(double value) {
  if (!Double.isFinite(value) || value <= 0) return 0.0;
  // Keep stored money values stable at two decimal places.
  return Math.round(value * 100.0) / 100.0;
 }

 private double categorySum(double... v) {
  double x = 0;
  for (double a : v) x += cleanMoney(a);
  return Math.round(x * 100.0) / 100.0;
 }

 @GetMapping("/dashboard")
 public String dashboard(HttpSession s, Model m) {
  User u = current(s);

  if (u == null) return "redirect:/login";

  fill(u, m);

  return "dashboard";
 }

 @GetMapping("/analytics")
 public String analytics(HttpSession s, Model m) {
  User u = current(s);

  if (u == null) return "redirect:/login";

  fill(u, m);

  return "analytics";
 }

 @GetMapping("/transactions")
 public String transactions(HttpSession s, Model m) {
  User u = current(s);

  if (u == null) return "redirect:/login";

  fill(u, m);

  return "transactions";
 }

 @GetMapping("/planning")
 public String planning(HttpSession s, Model m) {
  User u = current(s);

  if (u == null) return "redirect:/login";

  fill(u, m);

  return "planning";
 }

 @GetMapping("/monthly-plan")
 public String monthlyPlan(HttpSession s, Model m) {
  User u = current(s);

  if (u == null) return "redirect:/login";

  fill(u, m);

  return "monthly-plan";
 }

 @GetMapping("/budget")
 public String budget(HttpSession s, Model m) {
  User u = current(s);

  if (u == null) return "redirect:/login";

  fill(u, m);

  return "budget";
 }

 @GetMapping("/reports")
 public String reports(HttpSession s, Model m) {
  User u = current(s);

  if (u == null) return "redirect:/login";

  fill(u, m);

  return "reports";
 }

 private void fill(User u, Model m) {

  List<Expense> es =
          expenses.findByUserIdOrderByDateDesc(u.getId());

  List<Income> ins =
          incomes.findByUserIdOrderByDateDesc(u.getId());

  FinancialProfile p =
          profiles.findById(u.getId())
                  .orElse(new FinancialProfile());

  YearMonth ym = YearMonth.now();

  double importedThisMonth =
          es.stream()
                  .filter(e ->
                          e.getDate() != null &&
                                  YearMonth.from(e.getDate()).equals(ym))
                  .mapToDouble(Expense::getAmount)
                  .sum();

  double extraIn =
          ins.stream()
                  .filter(i ->
                          i.getDate() != null &&
                                  YearMonth.from(i.getDate()).equals(ym))
                  .mapToDouble(Income::getAmount)
                  .sum();

  double totalIncome =
          u.getMonthlySalary()
                  + u.getMonthlyOtherIncome()
                  + extraIn;

  // Planned spending comes from onboarding/category estimates. Actual
  // transactions are compared with it instead of being added on top of it.
  // This prevents the same expense from being counted twice.
  double plannedExpense = Math.max(0, u.getMonthlySpending());
  double actualExpense = Math.max(0, importedThisMonth);
  // Planned and actual expenses are alternatives, not two deductions.
  // If actual recorded spending is higher, use it; otherwise use the plan.
  double totalExpense = Math.max(plannedExpense, actualExpense);

  double balance = totalIncome - totalExpense;

  double spendingPct =
          totalIncome > 0
                  ? totalExpense / totalIncome * 100
                  : 0;

  // Spending Risk Factor: directly reflects how much of income is consumed.
  int riskFactor = (int) Math.round(Math.min(100, Math.max(0, spendingPct)));
  String riskLevel = riskFactor <= 30
          ? "Very Low Risk"
          : riskFactor <= 50
          ? "Low Risk"
          : riskFactor <= 70
          ? "Moderate Risk"
          : riskFactor <= 85
          ? "High Risk"
          : "Critical Risk";
  String riskColorClass = riskFactor <= 30
          ? "risk-dark-green"
          : riskFactor <= 50
          ? "risk-green"
          : riskFactor <= 70
          ? "risk-yellow"
          : riskFactor <= 85
          ? "risk-orange"
          : "risk-red";

  int day = LocalDate.now().getDayOfMonth();
  LocalDate today = LocalDate.now();
  LocalDate nextSalary;

  try {
   nextSalary = today.withDayOfMonth(Math.min(u.getSalaryDate(), today.lengthOfMonth()));
   if (!nextSalary.isAfter(today)) nextSalary = nextSalary.plusMonths(1);
   nextSalary = nextSalary.withDayOfMonth(Math.min(u.getSalaryDate(), nextSalary.lengthOfMonth()));
  } catch (Exception ex) {
   nextSalary = today.plusMonths(1);
  }

  int remainingDays = Math.max(1, (int) java.time.temporal.ChronoUnit.DAYS.between(today, nextSalary));

  double recent14 = es.stream()
          .filter(e -> e.getDate() != null && !e.getDate().isBefore(LocalDateTime.now().minusDays(14)))
          .mapToDouble(Expense::getAmount).sum();

  // Saving target is a desired monthly amount, never an additional expense.
  double desiredSaving = u.getSavingsTarget() > 0
          ? u.getSavingsTarget()
          : totalIncome * 0.20;
  double recommended = Math.min(Math.max(0, balance), Math.max(0, desiredSaving));

  // Money that can actually be spent after protecting the planned saving.
  double spendableAfterSaving = Math.max(0, balance - recommended);

  // Real category spending is allocated later, after the user's budget limits
  // are loaded. Until then, keep the full flexible pool intact.
  double unallocatedActualExpense = 0.0;
  double dailyFlexiblePool = spendableAfterSaving;
  int daysInMonth = ym.lengthOfMonth();
  double safeDaily = daysInMonth > 0 ? dailyFlexiblePool / daysInMonth : 0;
  double safeWeekly = Math.min(dailyFlexiblePool, safeDaily * 7.0);

  // Keep the historical spending rate only for the runway calculation.
  double daily = recent14 > 0
          ? recent14 / 14.0
          : (actualExpense > 0 ? actualExpense / Math.max(1, day) : plannedExpense / Math.max(1, day));

  // Runway is based on the amount that is actually available to spend.
  // Planned savings are already protected above and therefore are not part
  // of the runway calculation.
  double runway = daily > 0 ? spendableAfterSaving / daily : 999;

  double safe = 0;

  String risk = balance < 0
          ? "Expenses exceed income"
          : spendableAfterSaving <= 0 && recommended > 0
          ? "Saving target uses all remaining money"
          : runway <= 3
          ? "Salary likely to be exhausted"
          : runway <= 7
          ? "High risk"
          : runway <= 14
          ? "Warning"
          : "Safe";

  String riskClass = balance < 0 || spendableAfterSaving <= 0 && recommended > 0
          ? "danger"
          : runway <= 7 ? "high" : runway <= 14 ? "warn" : "safe";

  Map<String, Double> cats = new LinkedHashMap<>();

  // If real transactions are higher than the plan, show their actual category
  // distribution. Otherwise show the onboarding category plan.
  if (actualExpense > plannedExpense + 0.01) {
   for (Expense e : es) {
    if (e.getDate() != null && YearMonth.from(e.getDate()).equals(ym)) {
     String c = normalize(e.getCategory());
     cats.put(c, cats.getOrDefault(c, 0.0) + e.getAmount());
    }
   }
  } else {
   cats.put("Food", p.getFood());
   cats.put("Grocery", p.getGrocery());
   cats.put("Housing", p.getHousing());
   cats.put("Transport", p.getTravel());
   cats.put("Shopping", p.getShopping());
   cats.put("Bills", p.getUtilities());
   cats.put("Education", p.getEducation());
   cats.put("Healthcare", p.getHealthcare());
   cats.put("Entertainment", p.getEntertainment());
   cats.put("Other", p.getOther());
  }

  cats.entrySet().removeIf(e -> e.getValue() <= 0);
  double catTotal = cats.values().stream().mapToDouble(Double::doubleValue).sum();
  if (totalExpense > catTotal + 0.01) {
   cats.put("Other", cats.getOrDefault("Other", 0.0) + (totalExpense - catTotal));
  }

  double baseIncome =
          u.getMonthlySalary()
                  + u.getMonthlyOtherIncome();

  double baseExpense =
          u.getMonthlySpending();

  double goalProgress =
          u.getGoalAmount() > 0
                  ? Math.min(
                  100,
                  u.getCurrentSavings()
                          / u.getGoalAmount()
                          * 100
          )
                  : 0;

  double health =
          healthScore(
                  totalIncome,
                  totalExpense,
                  u.getCurrentSavings(),
                  u.getSavingsTarget(),
                  u.getGoalAmount()
          );

  String healthLabel =
          health >= 75
                  ? "Good"
                  : health >= 50
                  ? "Fair"
                  : "Needs attention";

  String balanceDisplay =
          balance >= 0
                  ? "₹" + money(balance)
                  : "−₹" + money(-balance);

  String balanceLabel =
          balance >= 0
                  ? "Available after expenses"
                  : "Over income";

  String balanceNegativeClass =
          balance < 0
                  ? "danger-stat"
                  : "";

  String riskIcon =
          riskClass.equals("safe")
                  ? "🟢"
                  : riskClass.equals("warn")
                  ? "🟡"
                  : riskClass.equals("high")
                  ? "🟠"
                  : "🔴";

  String runwayDisplay =
          runway < 999
                  ? String.format(
                  Locale.US,
                  "%.1f days",
                  runway
          )
                  : "∞";

  String goalTitle =
          (u.getGoalName() == null ||
                  u.getGoalName().isBlank())
                  ? "Savings goal"
                  : u.getGoalName();

  Map<String, Double> trends =
          monthlyTotals(es);

  List<String> recurring =
          recurring(es);

  double annualIncome =
          totalIncome * 12;

  double annualExpense =
          totalExpense * 12;

  double annualSaving = recommended * 12;

  Map<String, Double> categoryPercentages =
          new LinkedHashMap<>();

  for (Map.Entry<String, Double> ce :
          cats.entrySet()) {

   categoryPercentages.put(
           ce.getKey(),
           totalExpense > 0
                   ? Math.min(
                   100,
                   ce.getValue()
                           / totalExpense
                           * 100
           )
                   : 0
   );
  }

  Map<String, Double> trendPercentages =
          new LinkedHashMap<>();

  double monthlyBaseline =
          totalExpense;

  for (Map.Entry<String, Double> te :
          trends.entrySet()) {

   trendPercentages.put(
           te.getKey(),
           monthlyBaseline > 0
                   ? Math.min(
                   100,
                   te.getValue()
                           / monthlyBaseline
                           * 100
           )
                   : 0
   );
  }

  double one =
          project(
                  u.getCurrentSavings(),
                  recommended,
                  u.getInvestmentReturnRate(),
                  u.getSalaryGrowthRate(),
                  1
          );

  double five =
          project(
                  u.getCurrentSavings(),
                  recommended,
                  u.getInvestmentReturnRate(),
                  u.getSalaryGrowthRate(),
                  5
          );

  double ten =
          project(
                  u.getCurrentSavings(),
                  recommended,
                  u.getInvestmentReturnRate(),
                  u.getSalaryGrowthRate(),
                  10
          );

  Map<Integer, Double> yearlyProjections =
          new LinkedHashMap<>();

  List<Map<String, Object>> futureYears =
          new ArrayList<>();

  double futureBalance =
          u.getCurrentSavings();

  for (int y = 1; y <= 10; y++) {

   double yearStart =
           futureBalance;

   double yearIncome = 0;
   double yearExpense = 0;
   double yearSaved = 0;
   double yearInterest = 0;

   for (int mo = 1; mo <= 12; mo++) {

    double monthlyIncome =
            baseIncome *
                    Math.pow(
                            1 + u.getSalaryGrowthRate() / 100.0,
                            ((y - 1) * 12 + (mo - 1)) / 12.0
                    );

    double monthlyExpense =
            baseExpense;

    double contribution =
            Math.max(
                    0,
                    monthlyIncome - monthlyExpense
            );

    double beforeInterest =
            futureBalance;

    double interest =
            beforeInterest *
                    (u.getInvestmentReturnRate()
                            / 100.0
                            / 12.0);

    futureBalance =
            beforeInterest
                    + interest
                    + contribution;

    yearIncome += monthlyIncome;
    yearExpense += monthlyExpense;
    yearSaved += contribution;
    yearInterest += interest;
   }

   yearlyProjections.put(
           y,
           futureBalance
   );

   Map<String, Object> fy =
           new LinkedHashMap<>();

   fy.put("year", y);
   fy.put("start", yearStart);
   fy.put("income", yearIncome);
   fy.put("expense", yearExpense);
   fy.put("saved", yearSaved);
   fy.put("interest", yearInterest);
   fy.put("end", futureBalance);

   futureYears.add(fy);
  }

  one = yearlyProjections.get(1);
  five = yearlyProjections.get(5);
  ten = yearlyProjections.get(10);

  Map<String, Map<String, Double>> monthlyPlan =
          new LinkedHashMap<>();

  double planBalance =
          u.getCurrentSavings();

  for (int mo = 1; mo <= 12; mo++) {

   double growth =
           Math.pow(
                   1 + u.getSalaryGrowthRate() / 100.0,
                   (mo - 1) / 12.0
           );

   double estIncome =
           baseIncome * growth;

   double estExpense =
           baseExpense;

   double estSaving =
           Math.max(
                   0,
                   estIncome - estExpense
           );

   planBalance += estSaving;

   Map<String, Double> row =
           new LinkedHashMap<>();

   row.put("income", estIncome);
   row.put("expense", estExpense);
   row.put("saving", estSaving);
   row.put("balance", planBalance);

   monthlyPlan.put(
           YearMonth.now()
                   .plusMonths(mo - 1)
                   .format(
                           DateTimeFormatter.ofPattern(
                                   "MMM yyyy"
                           )
                   ),
           row
   );
  }

  double runwayMonths =
          daily > 0
                  ? u.getCurrentSavings()
                  / daily
                  / 30.0
                  : 0;

  Map<Integer, Double> dayTotals =
          new TreeMap<>();

  for (Expense e : es) {

   if (e.getDate() != null &&
           YearMonth.from(e.getDate()).equals(ym)) {

    int d =
            e.getDate().getDayOfMonth();

    dayTotals.put(
            d,
            dayTotals.getOrDefault(d, 0.0)
                    + e.getAmount()
    );
   }
  }

  List<Map<String, Object>> calendarCells =
          new ArrayList<>();

  int firstOffset =
          ym.atDay(1)
                  .getDayOfWeek()
                  .getValue() % 7;

  for (int i = 0; i < firstOffset; i++) {

   Map<String, Object> cell =
           new LinkedHashMap<>();

   cell.put("day", 0);
   cell.put("amount", 0.0);
   cell.put("hasExpense", false);

   calendarCells.add(cell);
  }

  for (int d = 1; d <= ym.lengthOfMonth(); d++) {

   Map<String, Object> cell =
           new LinkedHashMap<>();

   double amt =
           dayTotals.getOrDefault(d, 0.0);

   cell.put("day", d);
   cell.put("amount", amt);
   cell.put("hasExpense", amt > 0);

   calendarCells.add(cell);
  }

  List<Double> projection =
          new ArrayList<>();

  double projectionBalance =
          u.getCurrentSavings();

  double r =
          u.getInvestmentReturnRate()
                  / 100
                  / 12;

  for (int i = 1; i <= 12; i++) {

   projectionBalance =
           projectionBalance * (1 + r)
                   + recommended *
                   Math.pow(
                           1 + u.getSalaryGrowthRate() / 100,
                           (i - 1) / 12.0
                   );

   projection.add(projectionBalance);
  }

  double emergencyMonths =
          totalExpense > 0
                  ? u.getCurrentSavings()
                  / totalExpense
                  : 0;

  double goalGap =
          Math.max(
                  0,
                  u.getGoalAmount()
                          - u.getCurrentSavings()
          );

  Map<String, Double> budgets =
          new LinkedHashMap<>();

  // Budget & Goals limits are the single source of truth for live budgets.
  // A value of 0 intentionally means "do not track this category"; it must
  // never fall back to the original onboarding estimate.
  budgets.put("Food", Math.max(0, p.getBudgetFood()));
  budgets.put("Grocery", Math.max(0, p.getBudgetGrocery()));
  budgets.put("Housing", Math.max(0, p.getBudgetHousing()));
  budgets.put("Transport", Math.max(0, p.getBudgetTransport()));
  budgets.put("Shopping", Math.max(0, p.getBudgetShopping()));
  budgets.put("Utilities", Math.max(0, p.getBudgetUtilities()));
  budgets.put("Education", Math.max(0, p.getBudgetEducation()));
  budgets.put("Healthcare", Math.max(0, p.getBudgetHealthcare()));
  budgets.put("Entertainment", Math.max(0, p.getBudgetEntertainment()));
  budgets.put("Other", Math.max(0, p.getBudgetOther()));

  // Live budget status is based ONLY on recorded transactions.
  // Use the same 10 categories shown during account creation and in the
  // Budget & Goals editor. Planned amounts are never counted as spent.
  Map<String, Double> liveBudgetSpent = new LinkedHashMap<>();
  Map<String, Double> actualCategorySpend = new LinkedHashMap<>();
  for (String k : budgets.keySet()) {
   liveBudgetSpent.put(k, 0.0);
   actualCategorySpend.put(k, 0.0);
  }

  for (Expense e : es) {
   if (e.getDate() != null && YearMonth.from(e.getDate()).equals(ym)) {
    String key = normalize(e.getCategory());
    if (!budgets.containsKey(key)) key = "Other";
    actualCategorySpend.put(key,
            actualCategorySpend.getOrDefault(key, 0.0)
                    + Math.max(0, e.getAmount()));
   }
  }

  // Allocate recorded spending to its matching category budget first.
  // Any amount above that category's limit is redirected to Other, so the
  // existing flexible-money calculation remains consistent.
  double otherRemaining = Math.max(0, budgets.getOrDefault("Other", 0.0));
  for (String category : budgets.keySet()) {
   if ("Other".equals(category)) continue;
   double requested = actualCategorySpend.getOrDefault(category, 0.0);
   double limit = Math.max(0, budgets.getOrDefault(category, 0.0));
   double allocated = Math.min(requested, limit);
   liveBudgetSpent.put(category, allocated);

   double remainder = requested - allocated;
   if (remainder > 0 && otherRemaining > 0) {
    double otherAllocated = Math.min(remainder, otherRemaining);
    liveBudgetSpent.put("Other",
            liveBudgetSpent.get("Other") + otherAllocated);
    otherRemaining -= otherAllocated;
    remainder -= otherAllocated;
   }
   unallocatedActualExpense += Math.max(0, remainder);
  }

  double directOther = actualCategorySpend.getOrDefault("Other", 0.0);
  double directOtherAllocated = Math.min(directOther, otherRemaining);
  liveBudgetSpent.put("Other",
          liveBudgetSpent.get("Other") + directOtherAllocated);
  unallocatedActualExpense += Math.max(0, directOther - directOtherAllocated);

  Map<String, Double> budgetSpent = liveBudgetSpent;
  Map<String, Double> budgetRemainingByCategory = new LinkedHashMap<>();
  for (String k : budgets.keySet()) {
   budgetRemainingByCategory.put(k, Math.max(0, budgets.get(k) - budgetSpent.getOrDefault(k, 0.0)));
  }
  dailyFlexiblePool = Math.max(0, spendableAfterSaving - unallocatedActualExpense);
  safeDaily = daysInMonth > 0 ? dailyFlexiblePool / daysInMonth : 0;
  safeWeekly = Math.min(dailyFlexiblePool, safeDaily * 7.0);
  safe = safeDaily;

  Map<String, Double> budgetPercentages = new LinkedHashMap<>();
  for (String k : budgets.keySet()) {
   double spent = budgetSpent.getOrDefault(k, 0.0);
   double lim = budgets.getOrDefault(k, 0.0);
   budgetPercentages.put(k, lim > 0 ? Math.min(100, spent / lim * 100) : 0.0);
  }

  List<String> budgetAlerts =
          new ArrayList<>();

  for (String k : budgets.keySet()) {

   double budgetLimit =
           budgets.get(k);

   double sp =
           budgetSpent.get(k);

   if (budgetLimit > 0) {

    double pct =
            sp / budgetLimit * 100;

    if (pct >= 100) {

     budgetAlerts.add(
             "🔴 " + k
                     + " is over budget by ₹"
                     + money(sp - budgetLimit)
     );

    } else if (pct >= 90) {

     budgetAlerts.add(
             "🟠 " + k
                     + " has used "
                     + String.format(
                     Locale.US,
                     "%.0f",
                     pct
             )
                     + "% of its budget."
     );

    } else if (pct >= 80) {

     budgetAlerts.add(
             "🟡 " + k
                     + " has used "
                     + String.format(
                     Locale.US,
                     "%.0f",
                     pct
             )
                     + "% of its budget."
     );
    }
   }
  }

  List<String> healthReasons =
          new ArrayList<>();

  if (totalIncome > 0 &&
          totalExpense <= totalIncome) {

   healthReasons.add(
           "Your spending is within your monthly income."
   );

  } else {

   healthReasons.add(
           "Your current spending is above your monthly income."
   );
  }

  if (totalIncome > 0 &&
          Math.max(0, balance)
                  / totalIncome >= 0.2) {

   healthReasons.add(
           "You are keeping at least 20% of income available after expenses."
   );

  } else {

   healthReasons.add(
           "Try to protect a larger portion of your income for savings."
   );
  }

  if (emergencyMonths >= 3) {

   healthReasons.add(
           "Your current savings cover at least 3 months of baseline spending."
   );

  } else {

   healthReasons.add(
           "Building a 3-month emergency fund would strengthen your safety net."
   );
  }

  if (u.getGoalAmount() > 0 &&
          goalProgress >= 50) {

   healthReasons.add(
           "You are more than halfway to your current savings goal."
   );
  }

  List<String> insights =
          new ArrayList<>();

  insights.add(
          balance < 0
                  ? "Reduce spending to bring this month's plan back within income."
                  : "Your plan is currently within income."
  );

  if (!budgetAlerts.isEmpty()) {
   insights.add(budgetAlerts.get(0));
  }

  if (u.getGoalAmount() > 0 &&
          goalGap > 0 &&
          recommended > 0) {

   insights.add(
           "At your current estimated saving capacity, your goal could take about "
                   + (int) Math.ceil(goalGap / recommended)
                   + " months."
   );
  }

  if (trends.size() >= 2) {

   List<Double> tv =
           new ArrayList<>(trends.values());

   double last =
           tv.get(tv.size() - 1);

   double prev =
           tv.get(tv.size() - 2);

   if (prev > 0 &&
           last > prev * 1.1) {

    insights.add(
            "Spending increased by about "
                    + String.format(
                    Locale.US,
                    "%.0f",
                    (last / prev - 1) * 100
            )
                    + "% compared with the previous recorded month."
    );
   }
  }

  int goalMonths =
          goalGap > 0 &&
                  recommended > 0
                  ? (int) Math.ceil(
                  goalGap / recommended
          )
                  : 0;

  String goalEta =
          goalGap <= 0 &&
                  u.getGoalAmount() > 0
                  ? "Goal reached"
                  : goalMonths > 0
                  ? goalMonths
                  + " months at current saving rate"
                  : "Set a saving target to estimate time";

  int savingStreak = 0;

  for (int i = 1; i <= 3; i++) {

   YearMonth check =
           ym.minusMonths(i - 1);

   double spentMonth =
           es.stream()
                   .filter(e ->
                           e.getDate() != null &&
                                   YearMonth.from(e.getDate())
                                           .equals(check)
                   )
                   .mapToDouble(Expense::getAmount)
                   .sum();

   if (baseIncome > 0 &&
           spentMonth <= baseIncome) {

    savingStreak++;

   } else {

    break;
   }
  }

  List<String> badges =
          new ArrayList<>();

  if (balance >= 0) {
   badges.add("💚 Within income");
  }

  if (goalProgress >= 50) {
   badges.add("🎯 Halfway to goal");
  }

  if (emergencyMonths >= 3) {
   badges.add("🛡️ 3-month safety net");
  }

  // FIX: es is the List<Expense>; expenses is the repository.
  if (es.size() >= 10) {
   badges.add("📊 10+ transactions tracked");
  }

  if (recommended > 0) {
   badges.add("💰 Saving capacity found");
  }

  if (savingStreak >= 3) {
   badges.add("🔥 3-month saving streak");
  }

  double reduce =
          Math.max(
                  0,
                  totalExpense - totalIncome
          );

  String suggestion =
          reduce > 0
                  ? "Reduce approximately ₹"
                  + money(reduce)
                  + " to stay within your monthly income."
                  : balance > 0
                  ? "You are within income. Consider moving ₹"
                  + money(recommended)
                  + " into savings before discretionary spending."
                  : "Your spending is currently using all available income.";

  m.addAttribute("user", u);
  m.addAttribute("profile", p);
  m.addAttribute("expenses", es);
  m.addAttribute("incomes", ins);

  m.addAttribute("totalIncome", totalIncome);
  m.addAttribute("totalExpense", totalExpense);
  m.addAttribute("balance", balance);
  m.addAttribute("spendingPct", spendingPct);
  m.addAttribute("riskFactor", riskFactor);
  m.addAttribute("riskLevel", riskLevel);
  m.addAttribute("riskColorClass", riskColorClass);
  m.addAttribute("available", Math.max(0, balance));
  m.addAttribute("recommendedSaving", recommended);
  m.addAttribute("enteredMonthlySpending", u.getEnteredMonthlySpending());
  m.addAttribute("categoryExpenseTotal", u.getCategoryExpenseTotal());
  m.addAttribute("plannedExpense", plannedExpense);
  m.addAttribute("actualExpense", actualExpense);
  m.addAttribute("effectiveExpense", totalExpense);
  m.addAttribute("expensePlanAdjusted", u.getCategoryExpenseTotal() > u.getEnteredMonthlySpending() + 0.01);
  m.addAttribute("actualExpenseHigher", actualExpense > plannedExpense + 0.01);

  m.addAttribute("dailySpend", daily);
  m.addAttribute("remainingDays", remainingDays);
  m.addAttribute("runway", runway);
  m.addAttribute("runwayMonths", runwayMonths);
  m.addAttribute("safeToSpend", safe);
  m.addAttribute("safeDailySpend", safeDaily);
  m.addAttribute("safeWeeklySpend", safeWeekly);
  m.addAttribute("spendableAfterSaving", spendableAfterSaving);
  m.addAttribute("dailyFlexiblePool", dailyFlexiblePool);
  m.addAttribute("unallocatedActualExpense", unallocatedActualExpense);
  m.addAttribute("plannedSaving", recommended);

  m.addAttribute("risk", risk);
  m.addAttribute("riskClass", riskClass);

  m.addAttribute("categories", cats);

  m.addAttribute("health", health);
  m.addAttribute("healthLabel", healthLabel);

  m.addAttribute("balanceDisplay", balanceDisplay);
  m.addAttribute("balanceLabel", balanceLabel);
  m.addAttribute("balanceNegativeClass", balanceNegativeClass);

  m.addAttribute("riskIcon", riskIcon);
  m.addAttribute("runwayDisplay", runwayDisplay);

  m.addAttribute("goalTitle", goalTitle);

  m.addAttribute(
          "categoryPercentages",
          categoryPercentages
  );

  m.addAttribute(
          "trendPercentages",
          trendPercentages
  );

  m.addAttribute("annualIncome", annualIncome);
  m.addAttribute("annualExpense", annualExpense);
  m.addAttribute("annualSaving", annualSaving);

  m.addAttribute("oneYear", one);
  m.addAttribute("fiveYear", five);
  m.addAttribute("tenYear", ten);

  m.addAttribute(
          "projectionValues",
          projection.stream()
                  .map(v ->
                          String.format(
                                  Locale.US,
                                  "%.2f",
                                  v
                          )
                  )
                  .collect(Collectors.joining(","))
  );

  m.addAttribute(
          "categoryLabels",
          String.join("|", cats.keySet())
  );

  m.addAttribute(
          "categoryValues",
          cats.values()
                  .stream()
                  .map(v ->
                          String.format(
                                  Locale.US,
                                  "%.2f",
                                  v
                          )
                  )
                  .collect(Collectors.joining(","))
  );

  m.addAttribute("trends", trends);
  m.addAttribute("recurring", recurring);

  m.addAttribute(
          "emergencyMonths",
          emergencyMonths
  );

  m.addAttribute(
          "suggestion",
          suggestion
  );

  m.addAttribute(
          "goalProgress",
          goalProgress
  );

  m.addAttribute(
          "goalGap",
          Math.max(
                  0,
                  u.getGoalAmount()
                          - u.getCurrentSavings()
          )
  );

  m.addAttribute(
          "projectionLabels",
          "Month 1|Month 2|Month 3|Month 4|Month 5|Month 6|Month 7|Month 8|Month 9|Month 10|Month 11|Month 12"
  );

  m.addAttribute(
          "yearlyProjections",
          yearlyProjections
  );

  m.addAttribute(
          "futureYears",
          futureYears
  );

  m.addAttribute(
          "monthlyPlan",
          monthlyPlan
  );

  m.addAttribute(
          "actualAddedExpenses",
          actualExpense
  );

  m.addAttribute(
          "baseMonthlySpending",
          u.getMonthlySpending()
  );

  m.addAttribute(
          "deductionDisplay",
          balance < 0
                  ? "−₹" + money(-balance)
                  : "₹" + money(balance)
  );

  double totalBudgetLimit = budgets.values().stream().mapToDouble(Double::doubleValue).sum();
  double totalBudgetSpent = budgetSpent.values().stream().mapToDouble(Double::doubleValue).sum();
  double totalBudgetRemaining = Math.max(0, totalBudgetLimit - totalBudgetSpent);
  double budgetCoverage = totalBudgetLimit > 0
          ? Math.min(100, totalBudgetSpent / totalBudgetLimit * 100)
          : 0;

  m.addAttribute("budgets", budgets);
  m.addAttribute("budgetSpent", budgetSpent);
  m.addAttribute("actualCategorySpend", actualCategorySpend);
  m.addAttribute("budgetRemaining", totalBudgetRemaining);
  m.addAttribute("budgetRemainingByCategory", budgetRemainingByCategory);
  m.addAttribute("totalBudgetLimit", totalBudgetLimit);
  m.addAttribute("totalBudgetSpent", totalBudgetSpent);
  m.addAttribute("budgetCoverage", budgetCoverage);
  m.addAttribute(
          "budgetPercentages",
          budgetPercentages
  );

  m.addAttribute(
          "budgetAlerts",
          budgetAlerts
  );

  m.addAttribute(
          "healthReasons",
          healthReasons
  );

  m.addAttribute(
          "insights",
          insights
  );

  m.addAttribute(
          "goalEta",
          goalEta
  );

  m.addAttribute(
          "badges",
          badges
  );

  m.addAttribute(
          "goalMonths",
          goalMonths
  );

  m.addAttribute(
          "dayTotals",
          dayTotals
  );

  m.addAttribute(
          "calendarCells",
          calendarCells
  );

  m.addAttribute(
          "savingStreak",
          savingStreak
  );

  m.addAttribute(
          "currentMonthLabel",
          ym.format(
                  DateTimeFormatter.ofPattern(
                          "MMMM yyyy"
                  )
          )
  );

  m.addAttribute(
          "monthlySavingRate",
          totalIncome > 0
                  ? recommended / totalIncome * 100
                  : 0
  );
 }

 private String normalize(String c) {

  if (c == null || c.isBlank()) {
   return "Other";
  }

  String x =
          c.toLowerCase();

  if (x.contains("food") ||
          x.contains("restaurant") ||
          x.contains("grocery")) {

   return "Food";
  }

  if (x.contains("travel") ||
          x.contains("transport") ||
          x.contains("uber") ||
          x.contains("ola")) {

   return "Transport";
  }

  if (x.contains("shop")) {
   return "Shopping";
  }

  if (x.contains("bill") ||
          x.contains("rent") ||
          x.contains("utility")) {

   return "Bills";
  }

  if (x.contains("entertain")) {
   return "Entertainment";
  }

  if (x.contains("health")) {
   return "Healthcare";
  }

  if (x.contains("education")) {
   return "Education";
  }

  return "Other";
 }

 private Map<String, Double> monthlyTotals(
         List<Expense> es) {

  Map<String, Double> m =
          new LinkedHashMap<>();

  for (Expense e : es) {

   if (e.getDate() != null) {

    String k =
            YearMonth.from(e.getDate())
                    .toString();

    m.put(
            k,
            m.getOrDefault(k, 0.0)
                    + e.getAmount()
    );
   }
  }

  return m;
 }

 private List<String> recurring(
         List<Expense> es) {

  Map<String, List<Double>> m =
          new HashMap<>();

  for (Expense e : es) {

   String n =
           e.getNote() == null
                   ? ""
                   : e.getNote()
                   .trim()
                   .toLowerCase();

   if (n.isBlank()) {
    continue;
   }

   m.computeIfAbsent(
           n,
           k -> new ArrayList<>()
   ).add(e.getAmount());
  }

  return m.entrySet()
          .stream()
          .filter(e ->
                  e.getValue().size() >= 2)
          .map(Map.Entry::getKey)
          .limit(6)
          .collect(Collectors.toList());
 }

 private double healthScore(
         double income,
         double expense,
         double savings,
         double target,
         double goal) {

  double s = 0;

  if (income > 0) {

   s += Math.max(
           0,
           Math.min(
                   40,
                   (income - expense)
                           / income
                           * 40
           )
   );
  }

  if (expense <= income) {
   s += 20;
  }

  if (expense > 0) {

   s += Math.min(
           20,
           savings / expense * 10
   );
  }

  if (target <= 0 ||
          income - expense >= target) {

   s += 10;
  }

  if (goal <= 0 ||
          savings >= goal) {

   s += 10;
  }

  return Math.min(
          100,
          Math.round(s)
  );
 }

 private double project(
         double initial,
         double monthly,
         double ret,
         double growth,
         int years) {

  double b = initial;

  double r =
          ret / 100 / 12;

  for (int i = 1;
       i <= years * 12;
       i++) {

   b =
           b * (1 + r)
                   + monthly *
                   Math.pow(
                           1 + growth / 100,
                           (i - 1) / 12.0
                   );
  }

  return b;
 }

 private String money(double v) {

  return String.format(
          Locale.US,
          "%,.2f",
          v
  );
 }

 @PostMapping("/expense")
 public String addExpense(
         @RequestParam double amount,
         @RequestParam String category,
         @RequestParam(required = false) String note,
         HttpSession s) {

  User u = current(s);

  if (u == null) {
   return "redirect:/login";
  }

  saveExpense(
          u,
          amount,
          category,
          note,
          LocalDateTime.now(),
          "manual"
  );

  return "redirect:/dashboard";
 }

 @PostMapping("/income")
 public String addIncome(
         @RequestParam double amount,
         @RequestParam String source,
         @RequestParam(required = false) String note,
         HttpSession s) {

  User u = current(s);

  if (u == null) {
   return "redirect:/login";
  }

  if (amount > 0) {

   Income i = new Income();

   i.setUserId(u.getId());
   i.setAmount(amount);
   i.setSource(source.trim());
   i.setNote(
           note == null
                   ? ""
                   : note.trim()
   );
   i.setDate(
           LocalDateTime.now()
   );

   incomes.save(i);
  }

  return "redirect:/dashboard";
 }

 @PostMapping("/expense/voice")
 @ResponseBody
 public Map<String, Object> voice(
         @RequestParam double amount,
         @RequestParam String description,
         @RequestParam(
                 required = false,
                 defaultValue = "Other"
         ) String category,
         HttpSession s) {

  User u = current(s);

  if (u == null) {
   return Map.of(
           "ok",
           false,
           "message",
           "Login required"
   );
  }

  boolean added =
          saveExpense(
                  u,
                  amount,
                  category,
                  description,
                  LocalDateTime.now(),
                  "voice"
          );

  return Map.of(
          "ok",
          added,
          "message",
          added
                  ? "Voice expense added"
                  : "Duplicate transaction ignored"
  );
 }

 @PostMapping("/expense/ocr")
 @ResponseBody
 public Map<String, Object> ocr(
         @RequestParam double amount,
         @RequestParam String description,
         @RequestParam(
                 required = false,
                 defaultValue = "Other"
         ) String category,
         @RequestParam(required = false) String date,
         HttpSession s) {

  User u = current(s);

  if (u == null) {
   return Map.of(
           "ok",
           false,
           "message",
           "Login required"
   );
  }

  LocalDateTime dt =
          parseDate(date);

  boolean added =
          saveExpense(
                  u,
                  amount,
                  category,
                  description,
                  dt,
                  "ocr"
          );

  return Map.of(
          "ok",
          added,
          "message",
          added
                  ? "OCR transaction added"
                  : "Duplicate transaction ignored"
  );
 }

 private LocalDateTime parseDate(String d) {

  try {

   if (d == null || d.isBlank()) {
    return LocalDateTime.now();
   }

   String value = d.trim();
   try { return LocalDate.parse(value).atStartOfDay(); } catch (Exception ignored) { }
   for (DateTimeFormatter fmt : List.of(DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("d-M-uuuu"), DateTimeFormatter.ofPattern("M/d/uuuu"))) {
    try { return LocalDate.parse(value, fmt).atStartOfDay(); } catch (Exception ignored) { }
   }
   try { return LocalDateTime.parse(value.replace(' ', 'T')); } catch (Exception ignored) { }
   throw new IllegalArgumentException("Unsupported date");

  } catch (Exception e) {

   return LocalDateTime.now();
  }
 }

 private boolean saveExpense(
         User u,
         double amount,
         String category,
         String note,
         LocalDateTime date,
         String source) {

  if (amount <= 0) {
   return false;
  }

  String hash =
          hash(
                  u.getId()
                          + "|"
                          + String.format(
                          Locale.US,
                          "%.2f",
                          amount
                  )
                          + "|"
                          + date.toLocalDate()
                          + "|"
                          + (
                          note == null
                                  ? ""
                                  : note.trim()
                                  .toLowerCase()
                  )
          );

  if (expenses.existsByUserIdAndTransactionHash(
          u.getId(),
          hash)) {

   return false;
  }

  Expense e =
          new Expense();

  e.setUserId(u.getId());
  e.setAmount(amount);
  e.setCategory(
          normalize(category)
  );
  e.setNote(
          note == null
                  ? ""
                  : note.trim()
  );
  e.setDate(date);
  e.setSource(source);
  e.setTransactionHash(hash);

  expenses.save(e);

  return true;
 }

 private String hash(String s) {

  try {

   byte[] b =
           MessageDigest
                   .getInstance("SHA-256")
                   .digest(
                           s.getBytes(
                                   StandardCharsets.UTF_8
                           )
                   );

   StringBuilder x =
           new StringBuilder();

   for (byte a : b) {

    x.append(
            String.format(
                    "%02x",
                    a
            )
    );
   }

   return x.toString();

  } catch (Exception e) {

   return Integer.toHexString(
           s.hashCode()
   );
  }
 }

 @PostMapping("/expense/import")
 public String importFile(@RequestParam("file") MultipartFile file, HttpSession s) {
  User u = current(s);
  if (u == null) return "redirect:/login";
  if (file == null || file.isEmpty()) return "redirect:/transactions?importError=empty";
  int added = 0, dup = 0, skipped = 0;
  try {
   String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
   if (name.endsWith(".csv")) {
    String text = new String(file.getBytes(), StandardCharsets.UTF_8).replace("\uFEFF", "");
    List<String> lines = text.lines().toList();
    if (lines.isEmpty()) return "redirect:/transactions?importError=empty";
    List<String> header = parseCsvLine(lines.get(0));
    int amountCol = findHeader(header, "amount", "debit", "spent", "transaction amount", "amount (inr)", "expense amount", "withdrawal");
    int dateCol = findHeader(header, "date", "transaction date", "datetime", "txn date", "value date");
    int descCol = findHeader(header, "description", "merchant", "narration", "details", "particulars", "remarks", "payee", "transaction details");
    int catCol = findHeader(header, "category", "expense category");
    // If the first row isn't a recognizable header, accept the documented positional format.
    int firstData = (amountCol >= 0 || dateCol >= 0 || descCol >= 0) ? 1 : 0;
    if (amountCol < 0) amountCol = 0;
    if (dateCol < 0) dateCol = 1;
    if (descCol < 0) descCol = 2;
    for (int i=firstData;i<lines.size();i++) {
     if (lines.get(i).isBlank()) continue;
     try {
      List<String> cells=parseCsvLine(lines.get(i));
      double amt=parseAmount(cell(cells,amountCol));
      if (amt<=0) { skipped++; continue; }
      String date=cell(cells,dateCol), note=cell(cells,descCol);
      String category=catCol>=0?normalizeCategory(cell(cells,catCol)):guessCategory(note);
      if (note.isBlank()) note="Imported transaction";
      if(saveExpense(u,amt,category,note,parseDate(date),"csv")) added++; else dup++;
     } catch(Exception ignored){ skipped++; }
    }
   } else if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
    try(Workbook wb=WorkbookFactory.create(file.getInputStream())) {
     if(wb.getNumberOfSheets()==0) return "redirect:/transactions?importError=format";
     Sheet sh=wb.getSheetAt(0); DataFormatter formatter=new DataFormatter(Locale.US);
     Row headerRow=sh.getRow(sh.getFirstRowNum());
     List<String> headers=new ArrayList<>();
     if(headerRow!=null) for(int c=0;c<headerRow.getLastCellNum();c++){ Cell hc=headerRow.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL); headers.add(hc==null?"":formatter.formatCellValue(hc).trim()); }
     int amountCol=findHeader(headers,"amount","debit","spent","transaction amount","amount (inr)","expense amount","withdrawal");
     int dateCol=findHeader(headers,"date","transaction date","datetime","txn date","value date");
     int descCol=findHeader(headers,"description","merchant","narration","details","particulars","remarks","payee","transaction details");
     int catCol=findHeader(headers,"category","expense category");
     boolean hasHeader=amountCol>=0||dateCol>=0||descCol>=0;
     if(amountCol<0)amountCol=0;if(dateCol<0)dateCol=1;if(descCol<0)descCol=2;
     int first=hasHeader?sh.getFirstRowNum()+1:sh.getFirstRowNum();
     for(int i=first;i<=sh.getLastRowNum();i++){
      Row row=sh.getRow(i); if(row==null)continue;
      if(cell(row,amountCol,formatter).isBlank() && cell(row,descCol,formatter).isBlank() && (dateCol<0 || cell(row,dateCol,formatter).isBlank())) continue;
      double amt=parseAmount(cell(row,amountCol,formatter));
      if(amt<=0){skipped++;continue;}
      String note=cell(row,descCol,formatter); if(note.isBlank())note="Imported transaction";
      String date=cell(row,dateCol,formatter);
      Cell dc=row.getCell(dateCol);
      LocalDateTime dt=(dc!=null && dc.getCellType()==CellType.NUMERIC && DateUtil.isCellDateFormatted(dc))
        ? dc.getLocalDateTimeCellValue() : parseDate(date);
      String category=catCol>=0?normalizeCategory(cell(row,catCol,formatter)):guessCategory(note);
      if(saveExpense(u,amt,category,note,dt,"excel"))added++;else dup++;
     }
    }
   } else { return "redirect:/transactions?importError=format"; }
   return "redirect:/transactions?imported="+added+"&duplicates="+dup+"&skipped="+skipped;
  } catch(Exception e) {
   return "redirect:/transactions?importError=format";
  }
 }

 private List<String> parseCsvLine(String line) {
  List<String> out=new ArrayList<>(); StringBuilder value=new StringBuilder(); boolean quoted=false;
  for(int i=0;i<line.length();i++){
   char c=line.charAt(i);
   if(c=='"') { if(quoted && i+1<line.length() && line.charAt(i+1)=='"'){value.append('"');i++;}else quoted=!quoted; }
   else if(c==',' && !quoted){out.add(value.toString().trim());value.setLength(0);} else value.append(c);
  }
  out.add(value.toString().trim()); return out;
 }
 private int findHeader(List<String> headers,String... names){
  for(int i=0;i<headers.size();i++){String h=headers.get(i).replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT).replaceAll("[_-]"," ");for(String n:names)if(h.equals(n))return i;}return -1;
 }
 private String cell(List<String> cells,int i){return i>=0&&i<cells.size()?cells.get(i).trim():"";}
 private String cell(Row row,int i,DataFormatter f){if(row==null||i<0)return "";Cell c=row.getCell(i,Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);return c==null?"":f.formatCellValue(c).trim();}
 private double parseAmount(String value){
  if(value==null)return 0;
  try {
   String v=value.trim().replace("−","-").replaceAll("(?i)INR|Rs\\.?|₹|\\s", "").replace(",", "");
   if(v.startsWith("(")&&v.endsWith(")"))v="-"+v.substring(1,v.length()-1);
   // Expense imports accept positive debit amounts only; reject text and malformed values.
   return Double.parseDouble(v);
  } catch(Exception e){return 0;}
 }
 private String normalizeCategory(String value){
  if(value==null||value.isBlank())return "Other";String v=value.trim().toLowerCase(Locale.ROOT);
  return switch(v){case "food","restaurant","dining","lunch","dinner"->"Food";case "transport","travel","fuel","taxi"->"Transport";case "shopping","retail"->"Shopping";case "bills","utilities","recharge","electricity","water bill","internet"->"Utilities";case "entertainment","movies"->"Entertainment";case "healthcare","medical","health"->"Healthcare";case "education"->"Education";case "grocery","groceries"->"Grocery";case "housing","rent"->"Housing";default->"Other";};
 }
 private String guessCategory(String note){String n=note==null?"":note.toLowerCase(Locale.ROOT);
  if(n.matches(".*(food|restaurant|swiggy|zomato|cafe|coffee|lunch|dinner|hotel|biryani).*"))return "Food";
  if(n.matches(".*(uber|ola|fuel|petrol|bus|train|metro|rapido|transport).*"))return "Transport";
  if(n.matches(".*(grocery|supermarket|vegetable|milk).*"))return "Grocery";
  if(n.matches(".*(electricity|water bill|internet|recharge|bill).*"))return "Utilities";
  if(n.matches(".*(movie|netflix|spotify|game).*"))return "Entertainment";
  if(n.matches(".*(pharmacy|hospital|medical|doctor).*"))return "Healthcare";
  return "Other";
 }

 private String budgetCategory(String category) {
  if (category == null) return "Other";
  return switch (category) {
   case "Food", "Grocery", "Housing", "Transport", "Shopping", "Utilities",
        "Education", "Healthcare", "Entertainment", "Other" -> category;
   case "Bills" -> "Utilities";
   default -> "Other";
  };
 }

 private double budgetsSafeValue(FinancialProfile p, String category) {
  return switch (category) {
   case "Food" -> Math.max(0, p.getBudgetFood());
   case "Transport" -> Math.max(0, p.getBudgetTransport());
   case "Shopping" -> Math.max(0, p.getBudgetShopping());
   case "Bills" -> Math.max(0, p.getBudgetBills());
   case "Entertainment" -> Math.max(0, p.getBudgetEntertainment());
   case "Healthcare" -> Math.max(0, p.getBudgetHealthcare());
   case "Education" -> Math.max(0, p.getBudgetEducation());
   case "Other" -> Math.max(0, p.getBudgetOther());
   default -> 0.0;
  };
 }

 @PostMapping("/budget/update")
 public String updateBudget(
         @RequestParam double food,
         @RequestParam double grocery,
         @RequestParam double housing,
         @RequestParam double transport,
         @RequestParam double shopping,
         @RequestParam double utilities,
         @RequestParam double education,
         @RequestParam double healthcare,
         @RequestParam double entertainment,
         @RequestParam double other,
         HttpSession s) {

  User u = current(s);

  if (u == null) {
   return "redirect:/login";
  }

  FinancialProfile p =
          profiles.findById(u.getId())
                  .orElse(new FinancialProfile());

  p.setUserId(u.getId());

  // Save exactly the categories shown in the Finance Control/Budget editor.
  // These values are limits only; real transactions remain the spending data.
  p.setBudgetFood(Math.max(0, food));
  p.setBudgetGrocery(Math.max(0, grocery));
  p.setBudgetHousing(Math.max(0, housing));
  p.setBudgetTransport(Math.max(0, transport));
  p.setBudgetShopping(Math.max(0, shopping));
  p.setBudgetUtilities(Math.max(0, utilities));
  p.setBudgetEducation(Math.max(0, education));
  p.setBudgetHealthcare(Math.max(0, healthcare));
  p.setBudgetEntertainment(Math.max(0, entertainment));
  p.setBudgetOther(Math.max(0, other));

  // Keep the legacy aggregate field harmless for older records. The current
  // UI uses the explicit Utilities/Housing categories above.
  p.setBudgetBills(0);

  profiles.save(p);

  return "redirect:/budget?success";
 }

 @PostMapping("/profile/update")
 public String update(
         @RequestParam double salary,
         @RequestParam double otherIncome,
         @RequestParam int salaryDate,
         @RequestParam double monthlySpending,
         @RequestParam double currentSavings,
         @RequestParam double savingsTarget,
         @RequestParam(
                 required = false,
                 defaultValue = ""
         ) String goalName,
         @RequestParam double goalAmount,
         @RequestParam double salaryGrowthRate,
         @RequestParam double investmentReturnRate,
         HttpSession s) {

  User u = current(s);

  if (u == null) {
   return "redirect:/login";
  }

  u.setMonthlySalary(
          Math.max(0, salary)
  );

  u.setMonthlyOtherIncome(
          Math.max(0, otherIncome)
  );

  u.setSalaryDate(
          Math.min(
                  31,
                  Math.max(1, salaryDate)
          )
  );

  double editedSpending = cleanMoney(monthlySpending);
  u.setEnteredMonthlySpending(editedSpending);
  u.setMonthlySpending(editedSpending);
  u.setCategoryExpenseTotal(0.0);

  u.setCurrentSavings(
          Math.max(
                  0,
                  currentSavings
          )
  );

  u.setSavingsTarget(
          Math.max(
                  0,
                  savingsTarget
          )
  );

  u.setGoalName(
          goalName.trim()
  );

  u.setGoalAmount(
          Math.max(
                  0,
                  goalAmount
          )
  );

  u.setSalaryGrowthRate(
          Math.max(
                  0,
                  salaryGrowthRate
          )
  );

  u.setInvestmentReturnRate(
          Math.max(
                  0,
                  investmentReturnRate
          )
  );

  u.setUpdatedAt(
          LocalDateTime.now()
  );

  users.save(u);

  s.setAttribute(
          "loggedInUser",
          u
  );

  return "redirect:/settings?success";
 }
}