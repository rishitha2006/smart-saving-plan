package com.salarywise.model;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection="financial_profiles")
public class FinancialProfile {
    @Id private String userId;
    private double grocery, housing, travel, food, shopping, utilities, education, healthcare, entertainment, other;
    private double budgetFood, budgetGrocery, budgetHousing, budgetTransport, budgetShopping, budgetBills, budgetUtilities, budgetEntertainment, budgetHealthcare, budgetEducation, budgetOther;
    public FinancialProfile(){}
    public String getUserId(){return userId;} public void setUserId(String v){userId=v;}
    public double getGrocery(){return grocery;} public void setGrocery(double v){grocery=v;}
    public double getHousing(){return housing;} public void setHousing(double v){housing=v;}
    public double getTravel(){return travel;} public void setTravel(double v){travel=v;}
    public double getFood(){return food;} public void setFood(double v){food=v;}
    public double getShopping(){return shopping;} public void setShopping(double v){shopping=v;}
    public double getUtilities(){return utilities;} public void setUtilities(double v){utilities=v;}
    public double getEducation(){return education;} public void setEducation(double v){education=v;}
    public double getHealthcare(){return healthcare;} public void setHealthcare(double v){healthcare=v;}
    public double getEntertainment(){return entertainment;} public void setEntertainment(double v){entertainment=v;}
    public double getOther(){return other;} public void setOther(double v){other=v;}
    public double getBudgetFood(){return budgetFood;} public void setBudgetFood(double v){budgetFood=v;} public double getBudgetGrocery(){return budgetGrocery;} public void setBudgetGrocery(double v){budgetGrocery=v;} public double getBudgetHousing(){return budgetHousing;} public void setBudgetHousing(double v){budgetHousing=v;}
    public double getBudgetTransport(){return budgetTransport;} public void setBudgetTransport(double v){budgetTransport=v;}
    public double getBudgetShopping(){return budgetShopping;} public void setBudgetShopping(double v){budgetShopping=v;}
    public double getBudgetBills(){return budgetBills;} public void setBudgetBills(double v){budgetBills=v;} public double getBudgetUtilities(){return budgetUtilities;} public void setBudgetUtilities(double v){budgetUtilities=v;}
    public double getBudgetEntertainment(){return budgetEntertainment;} public void setBudgetEntertainment(double v){budgetEntertainment=v;}
    public double getBudgetHealthcare(){return budgetHealthcare;} public void setBudgetHealthcare(double v){budgetHealthcare=v;}
    public double getBudgetEducation(){return budgetEducation;} public void setBudgetEducation(double v){budgetEducation=v;}
    public double getBudgetOther(){return budgetOther;} public void setBudgetOther(double v){budgetOther=v;}
}
