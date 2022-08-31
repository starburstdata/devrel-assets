import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public interface TypeToken<T>
{
    default Type type()
    {
        Type[] genericInterfaces = getClass().getGenericInterfaces();
        if (genericInterfaces.length == 1) {
            if ((genericInterfaces[0] instanceof ParameterizedType parameterizedType) && (parameterizedType.getActualTypeArguments().length == 1)) {
                return parameterizedType.getActualTypeArguments()[0];
            }
        }
        throw new IllegalArgumentException("Badly specified type");
    }
}
