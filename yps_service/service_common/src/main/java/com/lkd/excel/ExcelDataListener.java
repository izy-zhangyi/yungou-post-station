package com.yps.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.google.common.collect.Lists;
import com.yps.entity.AbstractEntity;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeanUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

@Slf4j
public class ExcelDataListener<T extends AbstractEntity, E> extends AnalysisEventListener<E> {
    private Class<T> clazz; // 方便获取实体类实例对象
    /**
     * 每隔500条存储数据库
     */
    private static final int BATCH_COUNT = 500;

    private Function<Collection<T>, Boolean> saveFunc; // 代替 service 类的使用 -- 参数一：传递的参数类型-集合，参数二：返回值类型
    //批量存储的数据
    private List<T> list = Lists.newLinkedList();

    /**
     * 通过 AnalysisContext 对象还可以获取当前 sheet，当前行等数据
     */
    @Override
    public void invoke(E e, AnalysisContext analysisContext) {
        try {
            T t = clazz.getDeclaredConstructor().newInstance(); // 通过 class 获取实例对象（反射方式获取）
            BeanUtils.copyProperties(e, t); // 将 属性 拷贝到 实例对象中
            list.add(t); // 保存数据到集合中去
            if (list.size() >= BATCH_COUNT) {
                // 当集合中的数据 大于 500 时，直接去执行 doAfterAllAnalysed 方法
                this.doAfterAllAnalysed(null);
            }
        } catch (Exception ex) {
            log.error("读取 excel 失败", ex);
        }
    }

    /**
     * 读取完之后的操作
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        try {
            saveFunc.apply(list);
            list.clear();  // 保存完之后，清除集合中的所有数据
        } catch (Exception e) {
            log.error("存在重复条目");
        }
    }

    /**
     * 为了获取到传递的 class ，saveFunc--通过构造方法
     *
     * @param saveFunc 持久化数据到数据库的方法
     * @param clazz    对应的实体类类型
     */
    public ExcelDataListener(Function<Collection<T>, Boolean> saveFunc, Class<T> clazz) {

        this.saveFunc = saveFunc;
        this.clazz = clazz;
    }
}
