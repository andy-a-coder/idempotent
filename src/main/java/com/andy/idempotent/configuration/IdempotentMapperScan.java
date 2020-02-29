package com.andy.idempotent.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@MapperScan({
        "com.andy.idempotent.mapper",
})
@Configuration
public class IdempotentMapperScan {

}
