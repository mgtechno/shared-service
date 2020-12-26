package com.mgtechno.shared.jdbc;

import com.mgtechno.shared.Entity;
import com.mgtechno.shared.KeyValue;
import com.mgtechno.shared.util.CollectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class FinderService {
    private static final Logger LOG = Logger.getLogger(FinderService.class.getName());

    protected <B> List<B> load(Connection con, Class<B> clazz, List<KeyValue> criteria)throws Exception{
        List<B> beans = new ArrayList<>();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try{
            ps = con.prepareStatement(selectQuery(clazz, criteria));
            if (CollectionUtil.isNotEmpty(criteria)) {
                int i = 1;
                for (KeyValue kv : criteria) {
                    ps.setObject(i++, kv.getValue());
                }
            }
            rs = ps.executeQuery();
            while(rs.next()){
                B bean = clazz.getDeclaredConstructor().newInstance();
                Field[] fields = bean.getClass().getDeclaredFields();
                for(Field field: fields){
                    if(field.getAnnotation(MappedBy.class) != null){
                        continue;
                    }
                    field.setAccessible(true);
                    field.set(bean, rs.getObject(field.getName(), field.getType()));
                }


                List<Field> mappedFields = Arrays.stream(fields)
                        .filter(field -> field.getAnnotation(MappedBy.class) != null)
                        .collect(Collectors.toList());
                if(CollectionUtil.isNotEmpty(mappedFields)){
                    for(Field field: mappedFields){
                        field.setAccessible(true);
                        Field mapValueField = Arrays.stream(fields)
                                .filter(f -> f.getAnnotation(Id.class) != null).findFirst().get();
                        mapValueField.setAccessible(true);
                        List<KeyValue> mappedCriteria = new ArrayList<>();
                        mappedCriteria.add(new KeyValue(field.getAnnotation(MappedBy.class).property(), mapValueField.get(bean)));
                        if(field.getType().isAssignableFrom(Entity.class)){
                            List childBeans = load(con, field.getType(), mappedCriteria);
                            field.set(bean, childBeans.get(0));
                        }else if(field.getType().isAssignableFrom(Collection.class)){
                            Type genType = ((ParameterizedType)field.getGenericType()).getActualTypeArguments()[0];
                            List childBeans = load(con, genType.getClass(), mappedCriteria);
                            field.set(bean, childBeans);
                        }
                    }
                }
                beans.add(bean);
            }
        }finally {
            close(ps, rs);
        }
        return beans;
    }

    protected <B> List<B> findByQuery(Connection con, String query, List<KeyValue> criteria)throws Exception{
        List<B> result = new ArrayList<>();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try{
            ps = con.prepareStatement(query);
            if(CollectionUtil.isNotEmpty(criteria)){
                int i = 1;
                for(KeyValue kv : criteria){
                    ps.setObject(i++, kv.getValue());
                }
            }
            rs = ps.executeQuery();
            while(rs.next()){
                result.add((B)rs.getObject(1));
            }
        }finally {
            close(ps, rs);
        }
        return result;
    }

    private<B> String selectQuery(Class<B> clazz, List<KeyValue> criteria)throws Exception{
        StringBuilder query = new StringBuilder("select * from ");
        query.append(clazz.getSimpleName());
        if(CollectionUtil.isNotEmpty(criteria)){
            query.append(" where ");
            int j = 0;
            for(KeyValue kv : criteria){
                query.append(j++ > 0 ? " and " : "").append(kv.getKey()).append("=?");
            }
        }
        return query.toString();
    }

    private void close(PreparedStatement ps, ResultSet rs){
        try{
            if(ps != null){
                ps.close();
            }
            if(rs != null){
                rs.close();
            }
        }catch (Exception e){
            LOG.log(Level.SEVERE, "failed to close databae resources", e);
        }
    }
}
