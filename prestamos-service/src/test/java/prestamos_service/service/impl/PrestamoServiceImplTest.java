package prestamos_service.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.HttpClientErrorException;
import prestamos_service.builder.ComprobanteBuilder;
import prestamos_service.client.LibrosClient;
import prestamos_service.client.NotificacionesClient;
import prestamos_service.dto.request.NotificacionRequest;
import prestamos_service.dto.request.RegistrarDevolucionRequest;
import prestamos_service.dto.request.RegistrarPrestamoRequest;
import prestamos_service.dto.response.*;
import prestamos_service.entity.Prestamo;
import prestamos_service.factory.PrestamoFactory;
import prestamos_service.factory.PrestamoProcessor;
import prestamos_service.mapper.PrestamoMapper;
import prestamos_service.respository.PrestamoRepository;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrestamoServiceImplTest {


    @Mock
    private PrestamoRepository repository;

    @Mock
    private PrestamoMapper mapper;

    @Mock
    private LibrosClient librosClient;

    @Mock
    private NotificacionesClient notificacionesClient;

    @Mock
    private PrestamoFactory prestamoFactory;

    @Mock
    private PrestamoProcessor processor;

    @Mock
    private ComprobanteBuilder comprobanteBuilder;

    @InjectMocks
    private PrestamoServiceImpl prestamoServiceImpl;

    private RegistrarPrestamoRequest request;
    private Prestamo prestamo;

    @BeforeEach
    void setUp(){

        MockitoAnnotations.openMocks(this);

        prestamo = new Prestamo();
        prestamo.setCodigoPrestamo("PRE001");
        prestamo.setCodigoEjemplar("LIB001");
        prestamo.setCodigoSocio("SOC001");

        request = new RegistrarPrestamoRequest();
        request.setCodigoPrestamo("PRE001");
        request.setCodigoEjemplar("LIB001");
        request.setCodigoSocio("SOC001");

    }

    @Test
    void testRegistrarPrestamoCodigoDuplicado() {

        // Arrange
        PrestamoResponse prestamoResponse =
                PrestamoResponse.builder()
                        .codigoPrestamo("PRE001")
                        .codigoEjemplar("LIB001")
                        .codigoSocio("SOC001")
                        .estado("RECHAZADA")
                        .motivoRechazo("Código de préstamo ya registrado")
                        .build();

        ComprobantePrestamoResponse comprobante =
                ComprobantePrestamoResponse.builder()
                        .codigoPrestamo("PRE001")
                        .estado("RECHAZADA")
                        .mensaje("Código de préstamo ya registrado")
                        .build();

        when(repository.existsByCodigoPrestamo(anyString()))
                .thenReturn(true);

        when(mapper.toResponse(any(Prestamo.class)))
                .thenReturn(prestamoResponse);

        when(comprobanteBuilder.build(any(PrestamoResponse.class)))
                .thenReturn(comprobante);


        // Act
        ComprobantePrestamoResponse resultado =
                prestamoServiceImpl.registrarPrestamo(request);


        // Assert
        assertNotNull(resultado);
        assertEquals("RECHAZADA", resultado.getEstado());
        assertEquals("Código de préstamo ya registrado",
                resultado.getMensaje());

        verify(repository, never())
                .save(any(Prestamo.class));

        verify(librosClient, never())
                .actualizarDisponibilidad(anyString(), anyBoolean());

        verify(notificacionesClient, never())
                .enviar(any(NotificacionRequest.class));
    }
    
    @Test
    void testRegistrarPrestamoHappyPath(){


        // Arrange
        SocioResponse socio = SocioResponse.builder()
                .codigoSocio("SOC001")
                .email("juan@gmail.com")
                .activo(true)
                .build();

        EjemplarResponse ejemplar = EjemplarResponse.builder()
                .codigoEjemplar("LIB001")
                .disponible(true)
                .build();

        ApiResponse<SocioResponse> socioResponse =
                ApiResponse.<SocioResponse>builder()
                        .data(socio)
                        .build();

        ApiResponse<EjemplarResponse> ejemplarResponse =
                ApiResponse.<EjemplarResponse>builder()
                        .data(ejemplar)
                        .build();

        PrestamoResponse prestamoResponse =
                PrestamoResponse.builder()
                        .codigoPrestamo("PRE001")
                        .codigoEjemplar("LIB001")
                        .codigoSocio("SOC001")
                        .estado("REGISTRADA")
                        .build();

        ComprobantePrestamoResponse comprobante =
                ComprobantePrestamoResponse.builder()
                        .codigoPrestamo("PRE001")
                        .estado("REGISTRADA")
                        .mensaje("Préstamo registrado correctamente.")
                        .build();

        when(repository.existsByCodigoPrestamo(anyString()))
                .thenReturn(false);

        when(librosClient.obtenerSocio(anyString()))
                .thenReturn(socioResponse);

        when(librosClient.obtenerEjemplar(anyString()))
                .thenReturn(ejemplarResponse);

        when(prestamoFactory.obtenerProcesador(anyString()))
                .thenReturn(processor);

        when(processor.procesar(any(Prestamo.class)))
                .thenReturn(prestamo);

        when(repository.save(any(Prestamo.class)))
                .thenReturn(prestamo);

        when(mapper.toResponse(any(Prestamo.class)))
                .thenReturn(prestamoResponse);

        when(comprobanteBuilder.build(any(PrestamoResponse.class)))
                .thenReturn(comprobante);


        // Act
        ComprobantePrestamoResponse resultado =
                prestamoServiceImpl.registrarPrestamo(request);


        // Assert
        assertNotNull(resultado);
        assertEquals("PRE001", resultado.getCodigoPrestamo());
        assertEquals("REGISTRADA", resultado.getEstado());

        verify(repository).save(any(Prestamo.class));

        verify(librosClient)
                .actualizarDisponibilidad("LIB001", false);

        verify(notificacionesClient)
                .enviar(any(NotificacionRequest.class));

    }

    @Test
    void testRegistrarDevolucionHappyPath() {


        // Arrange
        RegistrarDevolucionRequest request = new RegistrarDevolucionRequest();
        request.setCodigoPrestamo("PRE001");

        prestamo.setEstado("REGISTRADA");
        prestamo.setCodigoEjemplar("LIB001");

        PrestamoResponse response = PrestamoResponse.builder()
                .codigoPrestamo("PRE001")
                .codigoEjemplar("LIB001")
                .estado("DEVUELTO")
                .build();

        when(repository.findByCodigoPrestamo(anyString()))
                .thenReturn(Optional.of(prestamo));

        when(prestamoFactory.obtenerProcesador("DEVOLUCION"))
                .thenReturn(processor);

        when(processor.procesar(any(Prestamo.class)))
                .thenReturn(prestamo);

        when(repository.save(any(Prestamo.class)))
                .thenReturn(prestamo);

        when(mapper.toResponse(any(Prestamo.class)))
                .thenReturn(response);


        // Act
        PrestamoResponse resultado =
                prestamoServiceImpl.registrarDevolucion(request);


        // Assert
        assertNotNull(resultado);
        assertEquals("DEVUELTO", resultado.getEstado());

        verify(repository).save(any(Prestamo.class));

        verify(librosClient)
                .actualizarDisponibilidad("LIB001", true);

        verify(processor)
                .procesar(any(Prestamo.class));

    }

    @Test
    void testRegistrarDevolucionPrestamoNoExiste() {


        // Arrange
        RegistrarDevolucionRequest request = new RegistrarDevolucionRequest();
        request.setCodigoPrestamo("PRE999");

        when(repository.findByCodigoPrestamo(anyString()))
                .thenReturn(Optional.empty());


        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> prestamoServiceImpl.registrarDevolucion(request));

        assertEquals("Préstamo no encontrado.",
                exception.getMessage());

        verify(repository, never())
                .save(any());

        verify(librosClient, never())
                .actualizarDisponibilidad(anyString(), anyBoolean());

    }

    @Test
    void testRegistrarDevolucionPrestamoYaDevuelto() {


        // Arrange
        RegistrarDevolucionRequest request = new RegistrarDevolucionRequest();
        request.setCodigoPrestamo("PRE001");

        prestamo.setEstado("DEVUELTO");

        when(repository.findByCodigoPrestamo(anyString()))
                .thenReturn(Optional.of(prestamo));


        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> prestamoServiceImpl.registrarDevolucion(request));

        assertEquals("El préstamo ya fue devuelto.",
                exception.getMessage());

        verify(repository, never())
                .save(any());

        verify(librosClient, never())
                .actualizarDisponibilidad(anyString(), anyBoolean());

        verify(prestamoFactory, never())
                .obtenerProcesador(anyString());

    }

    @Test
    void testListar() {


        // Arrange
        List<Prestamo> lista = List.of(prestamo);

        PrestamoResponse response = PrestamoResponse.builder()
                .codigoPrestamo("PRE001")
                .build();

        when(repository.findAll())
                .thenReturn(lista);

        when(mapper.toResponse(any(Prestamo.class)))
                .thenReturn(response);


        // Act
        List<PrestamoResponse> resultado =
                prestamoServiceImpl.listar();


        // Assert
       assertNotNull(resultado);
        assertEquals(1, resultado.size());

        verify(repository).findAll();

    }

    @Test
    void testBuscar() {


        // Arrange
        PrestamoResponse response = PrestamoResponse.builder()
                .codigoPrestamo("PRE001")
                .build();

        when(repository.findByCodigoPrestamo(anyString()))
                .thenReturn(Optional.of(prestamo));

        when(mapper.toResponse(any(Prestamo.class)))
                .thenReturn(response);


        // Act
        PrestamoResponse resultado =
                prestamoServiceImpl.buscar("PRE001");


        // Assert
        assertNotNull(resultado);
        assertEquals("PRE001", resultado.getCodigoPrestamo());

        verify(repository).findByCodigoPrestamo("PRE001");

    }
  
}