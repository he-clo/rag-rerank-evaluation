package com.gb.rag.Exception;

import com.gb.rag.Enum.ResultCodeEnum;
import lombok.Data;

@Data
public class LeaseException extends RuntimeException{

    private ResultCodeEnum resultCodeEnum;

    public LeaseException(String message) {
        super(message);
    }


    public LeaseException(ResultCodeEnum resultCodeEnum) {
        super(resultCodeEnum.getMessage());
        this.resultCodeEnum = resultCodeEnum;
    }
}
