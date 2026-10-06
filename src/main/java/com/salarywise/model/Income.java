package com.salarywise.model;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
@Document(collection="incomes")
public class Income {
    @Id private String id;
    private String userId, source, note;
    private double amount;
    private LocalDateTime date;
    public Income(){}
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getUserId(){return userId;} public void setUserId(String v){userId=v;}
    public String getSource(){return source;} public void setSource(String v){source=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
    public double getAmount(){return amount;} public void setAmount(double v){amount=v;}
    public LocalDateTime getDate(){return date;} public void setDate(LocalDateTime v){date=v;}
}
