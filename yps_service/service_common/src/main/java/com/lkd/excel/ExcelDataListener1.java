//package com.yps.excel;
//
//import com.alibaba.excel.context.AnalysisContext;
//import com.alibaba.excel.event.AnalysisEventListener;
//import com.google.common.collect.Lists;
//import com.yps.entity.AbstractEntity;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.BeanUtils;
//
//import java.util.Collection;
//import java.util.List;
//import java.util.function.Function;
//
///**
// * Excel解析批量存储
// * @param <T>
// */
//@Slf4j
//public class ExcelDataListener<T extends AbstractEntity,E> extends AnalysisEventListener<E> {
//    private Class<T> clazz;
//    /**
//     * 每隔500条存储数据库
//     */
//    private static final int BATCH_COUNT = 500;
//    //存储的具体操作
//    private Function<Collection<T>,Boolean> saveFunc;
//    //批量存储的数据
//    private List<T> list = Lists.newArrayList();
//    @Override
//    public void invoke(E e, AnalysisContext analysisContext) {
//        try {
//           T t = clazz.getDeclaredConstructor(null).newInstance();
//            BeanUtils.copyProperties(e,t);
//            list.add(t);
//            if (list.size() >= BATCH_COUNT) {
//                this.saveFunc.apply(list);
//                // 存储完成清理 list
//                list.clear();
//            }
//        } catch (Exception ex) {
//            log.error("create new instance error",ex);
//        }
//
//
//    }
//
//    @Override
//    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
//        this.saveFunc.apply(list);
//    }
//
//    public ExcelDataListener(Function<Collection<T>,Boolean> saveFunc,Class<T> clazz){
//        this.saveFunc = saveFunc;
//        this.clazz = clazz;
//    }
//}
