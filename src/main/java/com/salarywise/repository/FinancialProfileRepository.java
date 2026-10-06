package com.salarywise.repository;
import com.salarywise.model.FinancialProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface FinancialProfileRepository extends MongoRepository<FinancialProfile,String>{}
