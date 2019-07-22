package com.it.soul.lab.sql.entity;

import com.it.soul.lab.sql.QueryExecutor;
import com.it.soul.lab.sql.query.SQLDeleteQuery;
import com.it.soul.lab.sql.query.SQLInsertQuery;
import com.it.soul.lab.sql.query.QueryType;
import com.it.soul.lab.sql.query.SQLSelectQuery;
import com.it.soul.lab.sql.query.SQLUpdateQuery;
import com.it.soul.lab.sql.query.models.*;

import javax.persistence.Table;
import java.lang.reflect.Field;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

public abstract class Entity implements EntityInterface{
	public Entity() {
		super();
	}

	/**
	 *
	 * @param fieldName : must have to be the associated field name:
	 * @param exe
	 * @param skipPrimary
	 * @return
	 */
	protected Property getProperty(String fieldName, QueryExecutor exe, boolean skipPrimary) {
		Property result = null;
		try {
			Field field = getDeclaredField(fieldName, true);
			if(field.isAnnotationPresent(PrimaryKey.class)) {
				if (skipPrimary) {return null;}
				if (((PrimaryKey)field.getAnnotation(PrimaryKey.class)).autoIncrement() == true
						 && skipPrimary != false) {return null;}
			}
			field.setAccessible(true);
			String actualKey = getPropertyKey(field);
			Object value = getFieldValue(field, exe);
			result = new Property(actualKey, value);
			field.setAccessible(false);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException | SQLException e) {
			e.printStackTrace();
		}
		return result;
	}

	public final Field getDeclaredField(String fieldName, boolean inherit) throws NoSuchFieldException{
        //Search for the field, until get found any of the super class.
        //But not infinite round: MAX=8
        int maxLoopCount = 8;
        int loopCount = 0;
        Field field = null;
        Class mySClass = getClass();
        do{
            try{
                field = mySClass.getDeclaredField(fieldName);
            }catch (NoSuchFieldException | SecurityException e) {
                if (inherit == false) throw e;
                else mySClass = mySClass.getSuperclass();
            }
            if (loopCount++ > maxLoopCount) throw new NoSuchFieldException( fieldName + " does't exist!");
        }while(field == null);
        //
        return field;
    }

    private Object getFieldValue(Field field, QueryExecutor exe) throws IllegalArgumentException, IllegalAccessException, SQLException {
        Object value = field.get(this);
        //
        if(value == null && field.isAnnotationPresent(Column.class) == true) {
            Column annotation = field.getAnnotation(Column.class);
            String defaultVal = annotation.defaultValue();
            DataType type = annotation.type();
            switch (type) {
                case INT:
                    value = Integer.valueOf(defaultVal);
                    break;
                case FLOAT:
                    value = Float.valueOf(defaultVal);
                    break;
                case DOUBLE:
                    value = Double.valueOf(defaultVal);
                    break;
                case BOOL:
                    value = Boolean.valueOf(defaultVal);
                    break;
                case SQLDATE:
                case SQLTIMESTAMP:
                    value = parseDate(defaultVal, type, annotation.parseFormat());
                    break;
                case BLOB:
                    value = (exe != null) ? exe.createBlob(defaultVal) : defaultVal;
                    break;
                case BYTEARRAY:
                    value = defaultVal.getBytes();
                    break;
                default:
                    value = defaultVal;
                    break;
            }
        }
        //always.
        return value;
    }

    private java.util.Date parseDate(String val, DataType type, String format){
        try {
            SimpleDateFormat formatter = new SimpleDateFormat((format != null && format.trim().isEmpty() == false)
                    ? format
                    : Property.SQL_DATETIME_FORMAT);
            java.util.Date date = formatter.parse(val);
            if(type == DataType.SQLTIMESTAMP) {
                return new Timestamp(date.getTime());
            }else {
                return new Date(date.getTime());
            }
        }catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    protected List<Property> getProperties(QueryExecutor exe, boolean skipPrimary) {
        List<Property> result = new ArrayList<>();
        boolean acceptAll = shouldAcceptAllAsProperty();
        Field[] fields = getDeclaredFields(true);
        for (Field field : fields) {
            if(acceptAll == false && isFieldAnnotatedWith(field) == false) {
                continue;
            }
            Property prop = getProperty(field.getName(), exe, skipPrimary);
            if(prop == null) {continue;}
            result.add(prop);
        }
        return result;
    }

    protected boolean isFieldAnnotatedWith(Field field) {
        boolean isAnnotated = field.isAnnotationPresent(Column.class)
                || field.isAnnotationPresent(PrimaryKey.class);
        return isAnnotated;
    }

    public final Field[] getDeclaredFields(boolean inherit){
        List<Field> fields = new ArrayList<>();
        fields.addAll(Arrays.asList(getClass().getDeclaredFields()));
        if (inherit){
            //Inherit properties from one immediate parent which is not Entity.class.
            /*if (!getClass().getSuperclass().getSimpleName().equalsIgnoreCase(Entity.class.getSimpleName())){
                fields.addAll(Arrays.asList(getClass().getSuperclass().getDeclaredFields()));
            }*/
            Class mySuperClass = getClass().getSuperclass();
            while(!mySuperClass.getSimpleName().equalsIgnoreCase(Entity.class.getSimpleName())){
                fields.addAll(Arrays.asList(mySuperClass.getDeclaredFields()));
                mySuperClass = mySuperClass.getSuperclass();
            }
        }
        return fields.toArray(new Field[0]);
    }

	protected String getPropertyKey(Field field) {
		//Introduce Column:name() -> So that, if we want to mapping different column naming in Database Schema.
		//Logic: if column annotation not present OR Column:Name() is empty, then return field.getName() 
		//       else return Column:name()
		//Also consider @PrimaryKey:name() is also present.
		if(field.isAnnotationPresent(Column.class)) {
			Column column = field.getAnnotation(Column.class);
			String clName = column.name().trim();
			return (!clName.isEmpty()) ? clName : field.getName();
		}else if(field.isAnnotationPresent(PrimaryKey.class)) {
			PrimaryKey pm = field.getAnnotation(PrimaryKey.class);
			String pmName = pm.name().trim();
			return (!pmName.isEmpty()) ? pmName : field.getName();
		}else {
			return field.getName();
		}
	}

	protected boolean shouldAcceptAllAsProperty() {
		return Entity.shouldAcceptAllAsProperty(this.getClass());
	}

	private Boolean _isAutoIncremented = null;
	private boolean isAutoIncrement() {
		if(_isAutoIncremented == null) {
			PrimaryKey primAnno = getPrimaryKey();
			if(primAnno == null) {
				_isAutoIncremented = false;
			}
			_isAutoIncremented = primAnno.autoIncrement();
		}
		return _isAutoIncremented;
	}

    private List<PrimaryKey> getPrimaryKeys() {
        List<PrimaryKey> keys = new ArrayList<>();
        List<Field> fields = getPrimaryFields();
        for (Field field : fields) {
            if(field.isAnnotationPresent(PrimaryKey.class)) {
                keys.add(field.getAnnotation(PrimaryKey.class));
            }
        }
        return keys;
    }

	private PrimaryKey getPrimaryKey() {
		PrimaryKey key = null;
		List<Field> fields = getPrimaryFields();
		for (Field field : fields) {
			if(field.isAnnotationPresent(PrimaryKey.class)) {
				key = field.getAnnotation(PrimaryKey.class);
				break;
			}
		}
		return key;
	}

    protected List<Field> getPrimaryFields() {
        List<Field> keys = new ArrayList<>();
        Field[] fields = getDeclaredFields(true);
        for (Field field : fields) {
            if(field.isAnnotationPresent(PrimaryKey.class)) {
                keys.add(field);
            }
        }
        return keys;
    }

	protected List<Property> getPrimaryProperties(QueryExecutor exe) {
		List<Property> results = new ArrayList<>();
		try {
			List<Field> primaryFields = getPrimaryFields();
			if (primaryFields.isEmpty()) return results;
			//
			for (Field pmKeyField : primaryFields){
				String key = pmKeyField.getName();
				results.add(getProperty(key, exe, false));
			}
		} catch (SecurityException | IllegalArgumentException e) {
			e.printStackTrace();
		}
		return results;
	}

    private Property getPrimaryProperty(QueryExecutor exe) {
	    List<Property> all = getPrimaryProperties(exe);
        Property result = (all.size() > 0) ? all.get(0 ) : null;
        return result;
    }

    public Map<String, Object> marshallingToMap(boolean inherit) {
        Map<String, Object> result = new HashMap<>();
        for (Field field : getDeclaredFields(inherit)) {
			if (field.isAnnotationPresent(Ignore.class))
				continue;
            try {
                field.setAccessible(true);
                if (field.isAnnotationPresent(Column.class)){
                    Column column = field.getAnnotation(Column.class);
                    String columnName = (column.name().trim().isEmpty() == false) ? column.name().trim() : field.getName();
                    result.put(columnName, field.get(this));
                }else if(field.isAnnotationPresent(PrimaryKey.class)){
                    PrimaryKey primaryKey = field.getAnnotation(PrimaryKey.class);
                    String columnName = (primaryKey.name().trim().isEmpty() == false) ? primaryKey.name().trim() : field.getName();
                    result.put(columnName, field.get(this));
                }else{
                    result.put(field.getName(), field.get(this));
                }
                field.setAccessible(false);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

	public void unmarshallingFromMap(Map<String, Object> data, boolean inherit){
		if (data != null) {
			Field[] fields = getDeclaredFields(inherit);
			for (Field field : fields) {
				field.setAccessible(true);
				Object entry = data.get(field.getName());
				if(entry != null) {
					try {
						field.set(this, entry);
					} catch (IllegalAccessException e) {}
				}
				field.setAccessible(false);
			}
		}
	}

	/**
	 *
	 * @param exe
	 * @param keys
	 * @return
	 * @throws SQLException
	 * @throws Exception
	 */
	@Override
	public Boolean update(QueryExecutor exe, String...keys) throws SQLException {
		List<Property> properties = getPropertiesFromKeys(exe, keys, true);
		String tableName = Entity.tableName(getClass());
		SQLUpdateQuery query = exe.createQueryBuilder(QueryType.UPDATE)
														.set(properties.toArray(new Property[0]))
														.from(tableName)
														.where(primaryKeysInWhereExpression(exe)).build();
		int isUpdate = exe.executeUpdate(query);
		return isUpdate == 1;
	}
	protected ExpressionInterpreter primaryKeysInWhereExpression(QueryExecutor exe) {
		//return new Expression(getPrimaryProperty(null), Operator.EQUAL);
		List<Property> keys = getPrimaryProperties(exe);
		ExpressionInterpreter and = null;
		ExpressionInterpreter lhr = null;
		for (Property prop : keys) {
			if(lhr == null) {
				lhr = new Expression(prop, Operator.EQUAL);
				and = lhr;
			}else {
				ExpressionInterpreter rhr = new Expression(prop, Operator.EQUAL);
				and = new AndExpression(lhr, rhr);
				lhr = and;
			}
		}
		return and;
	}

	/**
	 *
	 * @param exe
	 * @param keys : must be field names associated with this class.
	 * @return
	 * @throws SQLException
	 * @throws Exception
	 */
	@Override
	public Boolean insert(QueryExecutor exe, String... keys) throws SQLException {
		List<Property> properties = getPropertiesFromKeys(exe, keys, false);
		SQLInsertQuery query = exe.createQueryBuilder(QueryType.INSERT)
															.into(Entity.tableName(getClass()))
															.values(properties.toArray(new Property[0])).build();
		
		int result = exe.executeInsert(isAutoIncrement(), query);
		if( result > 1 || isAutoIncrement()) {
			//update primary key to insert
			try {
				updateAutoID(result);
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		return result >= 1; //0=failed to insert, 1=successful to insert, >1=the auto incremented id which means inserted.
	}

	protected List<Property> getPropertiesFromKeys(QueryExecutor exe, String[] keys, boolean skipPrimary) {
		List<Property> properties = new ArrayList<>();
		if (keys.length > 0) {
			for (String key : keys) {
				String skey = key.trim();
				Property prop = getProperty(skey, exe, skipPrimary);
				if (prop == null) {
					continue;
				}
				properties.add(prop);
			}
		} else {
			properties = getProperties(exe, skipPrimary);
		}
		return properties;
	}

	private void updateAutoID(int insert) throws IllegalAccessException {
		List<Field> primaryFields = getPrimaryFields();
		if (primaryFields.isEmpty()) return;

		try {
			//Update any primary field that has autoIncrement = yes
			for (Field primaryField : primaryFields) {
				if (primaryField.isAnnotationPresent(PrimaryKey.class) == false) continue;
				if (primaryField.getAnnotation(PrimaryKey.class).autoIncrement() == false) continue;
				primaryField.setAccessible(true);
				primaryField.set(this, insert);
				primaryField.setAccessible(false);
				break;
			}
		} catch (SecurityException | IllegalArgumentException e) {
			e.printStackTrace();
		}
	}

	/**
	 *
	 * @param exe
	 * @return
	 * @throws SQLException
	 * @throws Exception
	 */
	@Override
	public Boolean delete(QueryExecutor exe) throws SQLException {
		//Expression exp = new Expression(getPrimaryProperty(exe), Operator.EQUAL);
		ExpressionInterpreter exp = primaryKeysInWhereExpression(exe);
		SQLDeleteQuery query = exe.createQueryBuilder(QueryType.DELETE)
														.rowsFrom(Entity.tableName(getClass()))
														.where(exp).build();
		int deletedId = exe.executeDelete(query);
		return deletedId == 1;
	}
	///////////////////////////////////////Class API///////////////////////////////////////
	protected static <T extends Entity> boolean shouldAcceptAllAsProperty(Class<T> type) {
		if(type.isAnnotationPresent(TableName.class) == false) {
			return false;
		}
		TableName tableName = type.getAnnotation(TableName.class);
		return tableName.acceptAll();
	}
    protected final static <T extends Entity> Field[] getDeclaredFields(Class<T> type, boolean inherit){
        Field[] fields = new Field[0];
        try {
            T newInstance = type.newInstance();
            fields = newInstance.getDeclaredFields(inherit);
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return fields;
    }
	public static <T extends Entity> Map<String, String> mapColumnsToProperties(Class<T> type) {

		boolean acceptAll = Entity.shouldAcceptAllAsProperty(type);
		if (acceptAll) {return null;}
		
		Map<String, String> result = new HashMap<>();
		for (Field field : Entity.getDeclaredFields(type, true)) {
			if(acceptAll == false
					&& field.isAnnotationPresent(Column.class) == false
					&& field.isAnnotationPresent(javax.persistence.Column.class) == false
					&& field.isAnnotationPresent(PrimaryKey.class) == false) {
				continue;
			}
			if (field.isAnnotationPresent(Column.class)){
				Column column = field.getAnnotation(Column.class);
				String columnName = (column.name().trim().isEmpty() == false) ? column.name().trim() : field.getName();
				result.put(columnName, field.getName());
			}else if(field.isAnnotationPresent(PrimaryKey.class)){
				PrimaryKey primaryKey = field.getAnnotation(PrimaryKey.class);
				String columnName = (primaryKey.name().trim().isEmpty() == false) ? primaryKey.name().trim() : field.getName();
				result.put(columnName, field.getName());
			}else if (field.isAnnotationPresent(javax.persistence.Column.class)){
                javax.persistence.Column column = field.getAnnotation(javax.persistence.Column.class);
                String columnName = (column.name().trim().isEmpty() == false) ? column.name().trim() : field.getName();
                result.put(columnName, field.getName());
            }
		}
		return result;
	}
	public static <T extends Entity> String tableName(Class<T> type) {
		if (type.isAnnotationPresent(TableName.class)){
			TableName tableName = type.getAnnotation(TableName.class);
			String name = (tableName.value().trim().length() == 0) ? type.getSimpleName() : tableName.value().trim();
			return name;
		}else if (type.isAnnotationPresent(Table.class)){
			Table tableName = type.getAnnotation(Table.class);
			String name = (tableName.name().trim().length() == 0) ? type.getSimpleName() : tableName.name().trim();
			return name;
		}else {
			return type.getSimpleName();
		}
	}
	public static <T extends Entity> List<T> read(Class<T>  type, QueryExecutor exe, Property...match) throws SQLException, Exception{
		ExpressionInterpreter and = null;
		ExpressionInterpreter lhr = null;
		for (int i = 0; i < match.length; i++) {
			if(lhr == null) {
				lhr = new Expression(match[i], Operator.EQUAL);
				and = lhr;
			}else {
				ExpressionInterpreter rhr = new Expression(match[i], Operator.EQUAL);
				and = new AndExpression(lhr, rhr);
				lhr = and;
			}
		}
		return T.read(type, exe, and);
	}
	public static <T extends Entity> List<T> read(Class<T>  type, QueryExecutor exe, ExpressionInterpreter expression) throws SQLException, Exception{
		String name = Entity.tableName(type);
		SQLSelectQuery query = null;
		if(expression != null) {
			query = exe.createQueryBuilder(QueryType.SELECT)
					.columns()
					.from(name)
					.where(expression).build();
		}else {
			query = exe.createQueryBuilder(QueryType.SELECT)
					.columns()
					.from(name).build();
		}
		return exe.executeSelect(query, type, Entity.mapColumnsToProperties(type));
	}
}
