package com.gen.core.service;

import org.springframework.beans.factory.annotation.Autowired;
import com.gen.core.context.UserContext;
import com.gen.core.db.TransactionDB;

public abstract class AbstractService {

    @Autowired
    private TransactionDB transaction;

    protected TransactionDB getTransaction() {
        return transaction;
    }


}
