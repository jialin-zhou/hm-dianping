package com.hmdp.config;

import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice // @RestControllerAdvice注解，用于全局处理控制器层抛出的异常
public class WebExceptionAdvice {

    /**
     * 处理运行时异常的异常处理器。
     *
     * @param e 发生的运行时异常。
     * @return 返回一个表示操作失败的结果对象，其中包含错误信息。
     *
     * 通过@ExceptionHandler(RuntimeException.class) 注解表示处理所有运行时异常
     * 在异常发生时，此方法会被调用，它会记录异常信息，并返回一个通用的错误响应，告诉用户"服务器异常"。
     */
    @ExceptionHandler(RuntimeException.class) // 指定处理所有运行时异常
    public Result handleRuntimeException(RuntimeException e) {
        // 记录异常信息
        log.error(e.toString(), e);
        // 返回一个失败结果，包含错误提示信息
        return Result.fail("服务器异常");
    }
}
