package com.salarywise.repository;
import com.salarywise.model.Expense; import org.springframework.data.mongodb.repository.MongoRepository; import java.util.*;
public interface ExpenseRepository extends MongoRepository<Expense,String>{ List<Expense> findByUserIdOrderByDateDesc(String userId); boolean existsByUserIdAndTransactionHash(String userId,String transactionHash); }
