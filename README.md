# 幂等组件 - idempotent
* 在软件开发中，重复提交、重复通知、补偿交易防重等场景的问题都属于需要处理的幂等问题；
* 一个幂等操作的特点，是任意多次执行所产生的影响均与一次执行的影响相同。重复执行不会影响系统状态，也不用担心会对系统数据造成破坏；
* 本组件就是为了解决这些问题而开发的，简单易用。支持幂等有效期设置、响应策略设置、幂等参数指定等特性。

## 1、使用方式（注解引用,非常方便）
通过在方法上添加@Idempotent注解来使用组件的功能
### 1）注解的几个简便使用示例
```
    @Idempotent
    public int saveUserAccount(UserAccountDto userAccount) {
        // ...
        return userAccountMapper.saveUserAccount(userAccount);
    }
    
    @Idempotent(idempotentMinutes = 1440)
    public int saveUserAccount(UserAccountDto userAccount) {
        // ...
        return userAccountMapper.saveUserAccount(userAccount);
    }
    
    @Idempotent(idempotentColumns = { "nickName", "sex" }, idempotentMinutes = 1440)
    public int saveUserAccount(String nickName, int sex) {
        // ...        
        return userAccountMapper.saveUserAccount(userAccount);
    }

    @Idempotent(idempotentColumns = { "userAccount.nickName", "userAccount.sex" }, idempotentMinutes = 1440)
    public int saveUserAccount(UserAccountDto userAccount) {
        // ...       
        return userAccountMapper.saveUserAccount(userAccount);
    }
    
    @Idempotent(idempotentColumns = { "userAccount.nickName", "userAccount.sex" }, idempotentMinutes = 1440, responseStrategy = 1)
    public int saveUserAccount(UserAccountDto userAccount) {
        // ...       
        return userAccountMapper.saveUserAccount(userAccount);
    }

```
### 2）注解的完整参数使用示例
```
    @Idempotent(
            idempotentColumns = { "userAccount.nickName", "userAccount.sex" }, 
            prjName = "test project", 
            interfaceName = "UserServiceImpl.saveUserAccount", 
            idempotentMinutes = 1440, 
            responseStrategy = 1
            idempotentParamOnly = true, 
            )
    public int saveUserAccount(UserAccountDto userAccount) {
        // ...
        return userAccountMapper.saveUserAccount(userAccount);
    }

```
### 3）注解的参数详解
```
idempotentColumns：唯一确定一次请求的参数集合(如果不设置，默认取所有参数)
prjName：服务名称（默认取spring.application.name定义的名称）
interfaceName：接口名称（默认取'类名.方法名'）
idempotentMinutes：幂等有效期时间（单位分钟，默认0-长期有效）
responseStrategy：响应策略（0-返回上次成功的请求结果（默认）；1-重复请求提醒）
idempotentParamOnly：请求参数是否只记录幂等字段（true/false, 默认false。有些参数如果记录下来比较大，比如，文件对象，可以设置为true，只记录幂等参数）

```
## 2、集成方式
### 1）下载源码，使用以下maven命令将组件打包install到本地仓库
```
mvn clean install -U -DskipTests
```
### 2）在自己的项目中添加maven依赖
```
        <dependency>
            <groupId>com.github.andy-a-coder</groupId>
            <artifactId>idempotent</artifactId>
            <version>1.0.0</version>
        </dependency>

```
### 3）yaml配置
本组件依赖mysql、redis、mybatis，按spring-boot的要求在自己的项目中配置即可

以下是配置示例

```
server:
  port: 8080
spring:
  application:
    name: idempotent-test
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/i_test?useUnicode=true&characterEncoding=utf-8&useSSL=false
    username: root
    password: 123456
    driver-class-name: com.mysql.jdbc.Driver
    type: com.zaxxer.hikari.HikariDataSource
    minimum-idle: 5
    maximum-pool-size: 100
    idle-timeout: 30000
    max-lifetime: 1800000
    connection-timeout: 30000
    connection-test-query: SELECT 1
  redis:
    host: 127.0.0.1
    port: 6379
    timeout: 5000
mybatis:
  configuration:
    map-underscore-to-camel-case: true  # 本组件使用的mybatis注解操作的idempotent_request表，加了这个配置可以把下划线分隔的库表字段自动转换为camel格式
    
```

### 4）创建幂等记录存储表（脚本见: /src/main/resources/idempotent.sql）



