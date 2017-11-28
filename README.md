# 基于Spring-statemachine的有限状态机(FSM)示例

## 前言
本文主要介绍一下状态机以及相关的一些概念。结合一个简单的订单状态流程，示例怎样在Springboot中集成Spring-statemachine。

## 有限状态机（Finite-state machine）

有限状态机（英语：finite-state machine，缩写：FSM），简称状态机，是表示**有限个状态**以及在这些状态之间的**转移和动作**等行为的数学模型。应用FSM模型可以帮助对象生命周期的状态的顺序以及导致状态变化的事件进行管理。将状态和事件控制从不同的业务Service方法的if else中抽离出来。FSM的应用范围很广，对于有复杂状态流，扩展性要求比较高的场景都可以使用该模型。下面是状态机模型中的4个要素，即现态、条件、动作、次态。

* 现态：是指当前所处的状态。
* 条件：又称为“事件”。当一个条件被满足，将会触发一个动作，或者执行一次状态的迁移。
* 动作：条件满足后执行的动作。动作执行完毕后，可以迁移到新的状态，也可以仍旧保持原状态。动作不是必需的，当条件满足后，也可以不执行任何动作，直接迁移到新状态。
* 次态：条件满足后要迁往的新状态。“次态”是相对于“现态”而言的，“次态”一旦被激活，就转变成新的“现态”了。

如下图示例：有限的状态集是“opend”以及“closed”。如果“现态”是“opend”,当“条件”为“Close”时，执行的“动作”是“close door”,次态则为“closed”。状态机逻辑执行完毕后“closed”则变成了“现态”。

![有限状态机](https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/Finite_state_machine_example_with_comments.svg/450px-Finite_state_machine_example_with_comments.svg.png) 


所以FSM的执行逻辑可以理解为下图,即FSM的下一个状态和输出是由输入和当前状态决定的：

![FSM的执行逻辑](https://upload.wikimedia.org/wikipedia/commons/thumb/6/64/Finite_State_Machine_Logic.svg/400px-Finite_State_Machine_Logic.svg.png)
 
## 集成Spring-statemachine框架到Springboot应用

对于使用Java语言的应用来说，可以选择的集成框架也比较多。如[squirrel-foundation](https://github.com/hekailiang/squirrel)，[spring-statemachine](https://github.com/spring-projects/spring-statemachine)，[stateless4j](https://github.com/oxo42/stateless4j) 。squirrel-foundation，stateless4j相对spring-statemachine更加轻量级。感兴趣的可以去看下这两个项目的源码。

Spring官网项目的QuickStart中没有对于持久化模型的举例。所以本文以一个持久化的流程——一个简单的示例订单流程进行举例。示例代码中，持久化框架使用了hibernate-jpa,请求的示范例子用了spring-web,spring-webmvc的Rest api。基于JDK8。Spring statemachine版本：1.2.7，SpringBoot 版本：1.5.3。       
     
### 配置

#### gradle 配置的版本      

```
dependencies {
    compile 'org.springframework.statemachine:spring-statemachine-core:1.2.7.RELEASE'
}  

```    

### 示例-订单的状态流程
如下图，本文示例一个简单的订单流程。
![订单状态图](http://img.blog.csdn.net/20171124154036100?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvbGlqaW5neWFvODIwNg==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/SouthEast)

#### 订单状态模型

状态枚举类**OrderStatus**：

```
public enum OrderStatus {

    // 待支付，待发货，待收货，订单结束
    WAIT_PAYMENT, WAIT_DELIVER, WAIT_RECEIVE, FINISH;
}

```

事件枚举类**OrderStatusChangeEvent**:

```
public enum OrderStatusChangeEvent {

    // 支付，发货，确认收货
    PAYED, DELIVERY, RECEIVED
}

```

订单Entity **Order**:

```  
@Entity
@Table(name = "order_test")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Integer id;

    @NotNull
    @Column(name = "order_id")
    private Integer orderId;

    @NotNull
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "status")
    private OrderStatus status;


    public Order() {
    }

    public Order(Integer orderId, OrderStatus status) {
        this.orderId = orderId;
        this.status = status;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId=" + orderId +
                ", status=" + status +
                '}';
    }
}

``` 

Repository类 **OrderRepo** ：

```
public interface OrderRepo extends JpaRepository<Order, Integer> {


    Order findByOrderId(Integer order);
}

```     
#### 初始化订单的状态集合以及状态转移事件
在启动springboot时，需要注入状态机的状态，事件的配置。起主要涉及到以下两个类：

1. StateMachineStateConfigurer<S, E>  配置状态集合以及初始状态，泛型参数S代表状态，E代表事件。
2. StateMachineTransitionConfigurer<S, E> 配置状态流的转移，可以定义状态转换接受的事件。

```
@SpringBootApplication
public class TestApp {

    public static void main(String[] args) {
        SpringApplication.run(TestApp.class, args);
    }

    @Configuration
    @EnableStateMachine
    static class StateMachineConfig
            extends StateMachineConfigurerAdapter<OrderStatus, OrderStatusChangeEvent> {
            
   		 @Override
        public void configure(StateMachineStateConfigurer<OrderStatus, OrderStatusChangeEvent> states)
                throws Exception {
            states
                    .withStates()
                    // 定义初始状态
                    .initial(OrderStatus.WAIT_PAYMENT)
                    // 定义所有状态集合
                    .states(EnumSet.allOf(OrderStatus.class));
        }
        @Override
        public void configure(StateMachineTransitionConfigurer<OrderStatus, OrderStatusChangeEvent> transitions)
                throws Exception {
            transitions
                    .withExternal()
                    .source(OrderStatus.WAIT_PAYMENT).target(OrderStatus.WAIT_DELIVER)
                    .event(OrderStatusChangeEvent.PAYED)
                    .and()
                    .withExternal()
                    .source(OrderStatus.WAIT_DELIVER).target(OrderStatus.WAIT_RECEIVE)
                    .event(OrderStatusChangeEvent.DELIVERY)
                    .and()
                    .withExternal()
                    .source(OrderStatus.WAIT_RECEIVE).target(OrderStatus.FINISH)
                    .event(OrderStatusChangeEvent.RECEIVED);
        }

    }
}

```  

#### 状态转移的监听器
状态转移过程中，可以通过监听器（Listener）来处理一些持久化或者业务监控等任务。在需要持久化的场景中，可以在状态机模式中的监听器中添加持久化的处理。其中主要涉及到：

1. StateMachineListener 事件监听器(通过Spring的event机制实现)，监听stateEntered(进入状态)、stateExited(离开状态)、eventNotAccepted(事件无法响应)、transition(转换)、transitionStarted(转换开始)、transitionEnded(转换结束)、stateMachineStarted(状态机启动)、stateMachineStopped(状态机关闭)、stateMachineError(状态机异常)等事件，借助listener可以跟踪状态转移。    
2. StateChangeInterceptor 拦截器接口，不同于Listener。其可以改变状态转移链的变化。主要在preEvent(事件预处理)、preStateChange(状态变更的前置处理)、postStateChange(状态变更的后置处理)、preTransition(转化的前置处理)、postTransition(转化的后置处理)、stateMachineError(异常处理)等执行点生效       
3. StateMachine 状态机实例，spring statemachine支持单例、工厂模式两种方式创建，每个statemachine有一个独有的machineId用于标识machine实例；需要注意的是statemachine实例内部存储了当前状态机等上下文相关的属性，因此这个实例不能够被多线程共享。    

为了方便扩展更多的Listener，以及管理Listeners和Interceptors。可以定义一个基于状态机实例的Handler: **PersistStateMachineHandler**,以及持久化实体的监听器**OrderPersistStateChangeListener**如下：

监听器的Handler以及接口定义**PersistStateMachineHandler**：

```
public class PersistStateMachineHandler extends LifecycleObjectSupport {

    private final StateMachine<OrderStatus, OrderStatusChangeEvent> stateMachine;
    private final PersistingStateChangeInterceptor interceptor = new PersistingStateChangeInterceptor();
    private final CompositePersistStateChangeListener listeners = new CompositePersistStateChangeListener();


    /**
     * 实例化一个新的持久化状态机Handler
     *
     * @param stateMachine 状态机实例
     */
    public PersistStateMachineHandler(StateMachine<OrderStatus, OrderStatusChangeEvent> stateMachine) {
        Assert.notNull(stateMachine, "State machine must be set");
        this.stateMachine = stateMachine;
    }

    @Override
    protected void onInit() throws Exception {
        stateMachine.getStateMachineAccessor().doWithAllRegions(function -> function.addStateMachineInterceptor(interceptor));
    }


    /**
     * 处理entity的事件
     *
     * @param event
     * @param state
     * @return 如果事件被接受处理，返回true
     */
    public boolean handleEventWithState(Message<OrderStatusChangeEvent> event, OrderStatus state) {
        stateMachine.stop();
        List<StateMachineAccess<OrderStatus, OrderStatusChangeEvent>> withAllRegions = stateMachine.getStateMachineAccessor()
                .withAllRegions();
        for (StateMachineAccess<OrderStatus, OrderStatusChangeEvent> a : withAllRegions) {
            a.resetStateMachine(new DefaultStateMachineContext<>(state, null, null, null));
        }
        stateMachine.start();
        return stateMachine.sendEvent(event);
    }

    /**
     * 添加listener
     *
     * @param listener the listener
     */
    public void addPersistStateChangeListener(PersistStateChangeListener listener) {
        listeners.register(listener);
    }


    /**
     * 可以通过 addPersistStateChangeListener，增加当前Handler的PersistStateChangeListener。
     * 在状态变化的持久化触发时，会调用相应的实现了PersistStateChangeListener的Listener实例。
     */
    public interface PersistStateChangeListener {

        /**
         * 当状态被持久化，调用此方法
         *
         * @param state
         * @param message
         * @param transition
         * @param stateMachine 状态机实例
         */
        void onPersist(State<OrderStatus, OrderStatusChangeEvent> state, Message<OrderStatusChangeEvent> message, Transition<OrderStatus,
                OrderStatusChangeEvent> transition,
                       StateMachine<OrderStatus, OrderStatusChangeEvent> stateMachine);
    }

    
    private class PersistingStateChangeInterceptor extends StateMachineInterceptorAdapter<OrderStatus, OrderStatusChangeEvent> {

        // 状态预处理的拦截器方法
        @Override
        public void preStateChange(State<OrderStatus, OrderStatusChangeEvent> state, Message<OrderStatusChangeEvent> message,
                                   Transition<OrderStatus, OrderStatusChangeEvent> transition, StateMachine<OrderStatus,
                OrderStatusChangeEvent> stateMachine) {
            listeners.onPersist(state, message, transition, stateMachine);
        }
    }

    private class CompositePersistStateChangeListener extends AbstractCompositeListener<PersistStateChangeListener> implements
            PersistStateChangeListener {

        @Override
        public void onPersist(State<OrderStatus, OrderStatusChangeEvent> state, Message<OrderStatusChangeEvent> message,
                              Transition<OrderStatus, OrderStatusChangeEvent> transition, StateMachine<OrderStatus,
                OrderStatusChangeEvent> stateMachine) {
            for (Iterator<PersistStateChangeListener> iterator = getListeners().reverse(); iterator.hasNext(); ) {
                PersistStateChangeListener listener = iterator.next();
                listener.onPersist(state, message, transition, stateMachine);
            }
        }
    }

}
```    

持久化状态发生变化的订单实体,**OrderPersistStateChangeListener**:        


```
public class OrderPersistStateChangeListener implements PersistStateMachineHandler.PersistStateChangeListener {


    @Autowired
    private OrderRepo repo;


    @Override
    public void onPersist(State<OrderStatus, OrderStatusChangeEvent> state, Message<OrderStatusChangeEvent> message,
                          Transition<OrderStatus, OrderStatusChangeEvent> transition, StateMachine<OrderStatus, OrderStatusChangeEvent> stateMachine) {
        if (message != null && message.getHeaders().containsKey("order")) {
            Integer order = message.getHeaders().get("order", Integer.class);
            Order o = repo.findByOrderId(order);
            OrderStatus status = state.getId();
            o.setStatus(status);
            repo.save(o);

        }
    }
}

```   

Springboot注入Handler和Listener bean的Configuration，**OrderPersistHandlerConfig**

```
@Configuration
public class OrderPersistHandlerConfig {

    @Autowired
    private StateMachine<OrderStatus, OrderStatusChangeEvent> stateMachine;


    @Bean
    public OrderStateService persist() {
        PersistStateMachineHandler handler = persistStateMachineHandler();
        handler.addPersistStateChangeListener(persistStateChangeListener());
        return new OrderStateService(handler);
    }

    @Bean
    public PersistStateMachineHandler persistStateMachineHandler() {
        return new PersistStateMachineHandler(stateMachine);
    }

    @Bean
    public OrderPersistStateChangeListener persistStateChangeListener(){
        return new OrderPersistStateChangeListener();
    }


}
```
#### Controller&Service 

示例提供了两个简单的接口，一个是查看所有订单列表，一个是改变一个订单的状态。

Controller如下**OrderController**:

```
@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderStateService orderStateService;

    /**
     * 列出所有的订单列表
     *
     * @return
     */
    @RequestMapping(method = {RequestMethod.GET})
    public ResponseEntity orders() {
        String orders = orderStateService.listDbEntries();
        return new ResponseEntity(orders, HttpStatus.OK);

    }


    /**
     * 通过触发一个事件，改变一个订单的状态
     * @param orderId
     * @param event
     * @return
     */
    @RequestMapping(value = "/{orderId}", method = {RequestMethod.POST})
    public ResponseEntity processOrderState(@PathVariable("orderId") Integer orderId, @RequestParam("event") OrderStatusChangeEvent event) {
        Boolean result = orderStateService.change(orderId, event);
        return new ResponseEntity(result, HttpStatus.OK);
    }

}

```    

订单服务类**OrderStateService**：


```
@Component
public class OrderStateService {

    private PersistStateMachineHandler handler;


    public OrderStateService(PersistStateMachineHandler handler) {
        this.handler = handler;
    }

    @Autowired
    private OrderRepo repo;


    public String listDbEntries() {
        List<Order> orders = repo.findAll();
        StringJoiner sj = new StringJoiner(",");
        for (Order order : orders) {
            sj.add(order.toString());
        }
        return sj.toString();
    }


    public boolean change(int order, OrderStatusChangeEvent event) {
        Order o = repo.findByOrderId(order);
        return handler.handleEventWithState(MessageBuilder.withPayload(event).setHeader("order", order).build(), o.getStatus());
    }

}
```    

#### 验证状态转移逻辑
添加测试数据，添加4个订单，分别是**WAIT_PAYMENT**状态。

##### 查询：
request:
> http://localhost:8089/orders

response:

```
{
 "data": "Order{orderId=1, status=WAIT_PAYMENT},Order{orderId=2, status=WAIT_PAYMENT},Order{orderId=3, status=WAIT_PAYMENT},Order{orderId=4, status=WAIT_PAYMENT}",
}
```

##### 变更状态：
request:  

> http://localhost:8089/orders/1?event=PAYED   
> http://localhost:8089/orders/2?event=PAYED   
> http://localhost:8089/orders/2?event=DELIVERY 
> http://localhost:8089/orders/2?event=DELIVERY     

response:

```
{
"data": true
}
```

```
{
"data": true
}
```

```
{
"data": true
}
```

```
{
"data": false
}
```

##### 验证持久化后的状态：

request:
> http://localhost:8089/orders

response:

```
{
 "data": "Order{orderId=1, status=WAIT_DELIVER},Order{orderId=2, status=WAIT_RECEIVE},Order{orderId=3, status=WAIT_PAYMENT},Order{orderId=4, status=WAIT_PAYMENT}",
}
```


## 相关资源
本文主要是结合一个简单示例示范Spring-statemachine的集成，在实际的业务实施中还会有更多复杂的情况，比如事务的处理，分布式事件消息，资源锁等。LZ最近也在对业务进行重构，欢迎一起交流技术。     


[Spring-statemachine 官网](https://projects.spring.io/spring-statemachine/)
