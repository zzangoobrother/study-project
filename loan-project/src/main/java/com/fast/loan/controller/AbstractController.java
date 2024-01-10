package com.fast.loan.controller;

import com.fast.loan.dto.ResponseDTO;
import com.fast.loan.dto.ResultObject;

public abstract class AbstractController {

    protected <T> ResponseDTO<T> ok() {
        return ok(null, ResultObject.getSuccess());
    }

    protected <T> ResponseDTO<T> ok(T data) {
        return ok(data, ResultObject.getSuccess());
    }

    protected <T> ResponseDTO<T> ok(T data, ResultObject resultObject) {
        ResponseDTO<T> response = new ResponseDTO<>();
        response.setResult(resultObject);
        response.setData(data);

        return ok(data, ResultObject.getSuccess());
    }
}
