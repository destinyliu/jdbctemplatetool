package org.crazycake.jdbcTemplateTool.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.crazycake.jdbcTemplateTool.context.EntityMetainfo;
import org.crazycake.jdbcTemplateTool.context.PropertyMetainfo;
import org.crazycake.jdbcTemplateTool.context.SimpledaoContext;
import org.crazycake.jdbcTemplateTool.exception.NoColumnAnnotationFoundException;
import org.crazycake.jdbcTemplateTool.exception.NoDefinedGetterException;
import org.crazycake.jdbcTemplateTool.exception.NoIdAnnotationFoundException;
import org.crazycake.jdbcTemplateTool.model.SqlParamsPairs;
import org.crazycake.utils.CamelNameUtils;

/**
 * Turn model to sql
 * @author Administrator
 *
 */
public class SqlBuilder {
	
	private static Log logger = LogFactory.getLog(SqlBuilder.class);
	
	/**
	 * 从po对象中分析出insert语句
	 * @param po
	 * @return
	 * @throws NoSuchMethodException 
	 * @throws SecurityException 
	 */
	public static <T> SqlParamsPairs buildInsert(T po) throws Exception{
		
		//用来存放insert语句
		StringBuffer insertSql = new StringBuffer();
		//用来存放?号的语句
		StringBuffer paramsSql = new StringBuffer();
		
		//用来存放参数值
		List<Object> params = new ArrayList<Object>();
		
		//分析表名
		//String tableName = getTableName(po.getClass());
		EntityMetainfo entityMetainfo = SimpledaoContext.get().getEntityMetainfo(po.getClass());
		
		insertSql.append("insert into " + entityMetainfo.getTable() + " (");
		
		//计数器
		int count=0;
		
		//分析列
		//Field[] fields = po.getClass().getDeclaredFields();
		List<PropertyMetainfo> propMetas = entityMetainfo.getPropertyMetainfos();
		for (int i = 0; i < propMetas.size(); i++) {
			PropertyMetainfo propMeta = propMetas.get(i);
			
			if(propMeta.isTransient()) {
				continue;
			}
			Object value = propMeta.getProperty().getReadMethod().invoke(po);
			if(value == null){
				//如果参数值是null就直接跳过（不允许覆盖为null值，规范要求更新的每个字段都要有值，没有值就是空字符串）
				continue;
			}
			
			if(count!=0){
				insertSql.append(",");
			}
			insertSql.append(propMeta.getColumn());
			
			if(count!=0){
				paramsSql.append(",");
			}
			paramsSql.append("?");
			
			params.add(value);
			count++;
		}
		
		insertSql.append(") values (");
		insertSql.append(paramsSql + ")");
		
		SqlParamsPairs sqlAndParams = new SqlParamsPairs(insertSql.toString(), params.toArray());
		logger.debug(sqlAndParams.toString());
		
		return sqlAndParams;
		
	}

	/**
	 * 从对象中获取update语句
	 * @param po
	 * @return
	 * @throws Exception
	 */
	public static SqlParamsPairs buildUpdate(Object po) throws Exception{
		
		//用来存放insert语句
		StringBuffer updateSql = new StringBuffer();
		
		//用来存放where语句
		StringBuffer whereSql = new StringBuffer();
		
		//用来存放参数值
		List<Object> params = new ArrayList<Object>();
		
		//用来存储id
		Object idValue = null;
		
		//分析表名
		//String tableName = getTableName(po.getClass());
		EntityMetainfo entityMetainfo = SimpledaoContext.get().getEntityMetainfo(po.getClass());
		
		updateSql.append("update " + entityMetainfo.getTable() + " set");
		
		//分析列
		//Field[] fields = po.getClass().getDeclaredFields();
		List<PropertyMetainfo> propMetas = entityMetainfo.getPropertyMetainfos();
		
		//用于计数
		int count = 0;
		for (int i = 0; i < propMetas.size(); i++) {
			PropertyMetainfo propMeta = propMetas.get(i);
			
			if(propMeta.isTransient()) {
				continue;
			}
			Object value = propMeta.getProperty().getReadMethod().invoke(po);
			if(value == null){
				//如果参数值是null就直接跳过（不允许覆盖为null值，规范要求更新的每个字段都要有值，没有值就是空字符串）
				continue;
			}
			
			//获取字段名
			String columnName = propMeta.getColumn();
			
			//看看是不是主键
			if(columnName.equals(entityMetainfo.getPrimaryKey())){
				//如果是主键
				whereSql.append(columnName + " = ?");
				idValue = value;
				continue;
			}
			
			//如果是普通列
			params.add(value);
			
			if(count!=0){
				updateSql.append(",");
			}
			updateSql.append(" " + columnName + " = ?");
			
			count++;
		}
		
		updateSql.append(" where ");
		updateSql.append(whereSql);
		params.add(idValue);
		
		SqlParamsPairs sqlAndParams = new SqlParamsPairs(updateSql.toString(),params.toArray());
		logger.debug(sqlAndParams.toString());
		
		return sqlAndParams;
		
	}
	
	/**
	 * 从对象中获取delete语句
	 * @param po
	 * @return
	 * @throws Exception
	 */
	public static SqlParamsPairs buildDelete(Object po) throws Exception{
		
		//用来存放insert语句
		StringBuffer deleteSql = new StringBuffer();
		
		//用来存储id
		Object idValue = null;
		
		//分析表名
		EntityMetainfo entityMeta = SimpledaoContext.get().getEntityMetainfo(po.getClass());
		
		deleteSql.append("delete from " + entityMeta.getTable() + " where ");
		
		deleteSql.append(entityMeta.getPrimaryKey() + " = ?");
		
		idValue = entityMeta.getPrimaryKeyProperty().getProperty()
				.getReadMethod().invoke(po, new Object[] {});	
		
		SqlParamsPairs sqlAndParams = new SqlParamsPairs(deleteSql.toString(),new Object[]{idValue});
		logger.debug(sqlAndParams.toString());
		
		return sqlAndParams;
		
	}
	
	/**
	 * 获取根据主键查对象的sql和参数
	 * @param po
	 * @param id
	 * @return
	 * @throws NoIdAnnotationFoundException 
	 * @throws NoColumnAnnotationFoundException 
	 * @throws NoDefinedGetterException 
	 * @throws  
	 * @throws Exception 
	 */
	public static <T> SqlParamsPairs buildGet(Class<T> clazz,Object id) throws NoIdAnnotationFoundException, NoColumnAnnotationFoundException{
		
		//用来存放get语句
		StringBuffer getSql = new StringBuffer();
		
		EntityMetainfo entityMeta = SimpledaoContext.get().getEntityMetainfo(clazz);
		
		getSql.append("select * from " + entityMeta.getTable() + " where ");
		
		getSql.append(entityMeta.getPrimaryKey() + " = ?");
			
		SqlParamsPairs sqlAndParams = new SqlParamsPairs(getSql.toString(),new Object[]{id});
		logger.debug(sqlAndParams.toString());
		
		return sqlAndParams;
	}
	
	public static void buildSelect(Class<?> clazz) {
		
	}
 	
	
}
