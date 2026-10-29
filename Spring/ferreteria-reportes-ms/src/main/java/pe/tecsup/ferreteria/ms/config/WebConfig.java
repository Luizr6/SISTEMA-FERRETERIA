package pe.tecsup.ferreteria.ms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import pe.tecsup.ferreteria.ms.seguridad.AutenticacionInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FerreteriaProperties props;
    private final AutenticacionInterceptor autenticacion;

    public WebConfig(FerreteriaProperties props, AutenticacionInterceptor autenticacion) {
        this.props = props;
        this.autenticacion = autenticacion;
    }

    /** Todas las rutas /api/** pasan por la validación del JWT de Django (salvo /api/salud). */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(autenticacion)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/salud");
    }

    /** El panel React puede llamar directamente a este servicio. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.cors().origenes().toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-API-Key")
                .exposedHeaders("Content-Disposition");
    }
}
