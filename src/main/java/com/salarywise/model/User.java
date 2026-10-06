package com.salarywise.model;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
@Document(collection="users")
public class User {
 @Id private String id; private String name,email,username,password,address,goalName; private boolean onboardingComplete;
 private double monthlySalary,monthlyOtherIncome,monthlySpending,currentSavings,savingsTarget,salaryGrowthRate,investmentReturnRate,goalAmount;
 private double enteredMonthlySpending,categoryExpenseTotal;
 private int salaryDate=1; private LocalDateTime createdAt,updatedAt;
 public User(){}
 public String getId(){return id;} public void setId(String v){id=v;} public String getName(){return name;} public void setName(String v){name=v;}
 public String getEmail(){return email;} public void setEmail(String v){email=v;} public String getUsername(){return username;} public void setUsername(String v){username=v;} public String getPassword(){return password;} public void setPassword(String v){password=v;} public String getAddress(){return address;} public void setAddress(String v){address=v;}
 public boolean isOnboardingComplete(){return onboardingComplete;} public void setOnboardingComplete(boolean v){onboardingComplete=v;}
 public double getMonthlySalary(){return monthlySalary;} public void setMonthlySalary(double v){monthlySalary=v;} public double getMonthlyOtherIncome(){return monthlyOtherIncome;} public void setMonthlyOtherIncome(double v){monthlyOtherIncome=v;}
 public double getMonthlySpending(){return monthlySpending;} public void setMonthlySpending(double v){monthlySpending=v;}
 public double getEnteredMonthlySpending(){return enteredMonthlySpending;} public void setEnteredMonthlySpending(double v){enteredMonthlySpending=v;}
 public double getCategoryExpenseTotal(){return categoryExpenseTotal;} public void setCategoryExpenseTotal(double v){categoryExpenseTotal=v;} public double getCurrentSavings(){return currentSavings;} public void setCurrentSavings(double v){currentSavings=v;}
 public double getSavingsTarget(){return savingsTarget;} public void setSavingsTarget(double v){savingsTarget=v;} public double getSalaryGrowthRate(){return salaryGrowthRate;} public void setSalaryGrowthRate(double v){salaryGrowthRate=v;} public double getInvestmentReturnRate(){return investmentReturnRate;} public void setInvestmentReturnRate(double v){investmentReturnRate=v;}
 public int getSalaryDate(){return salaryDate;} public void setSalaryDate(int v){salaryDate=v;} public String getGoalName(){return goalName;} public void setGoalName(String v){goalName=v;} public double getGoalAmount(){return goalAmount;} public void setGoalAmount(double v){goalAmount=v;}
 public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
