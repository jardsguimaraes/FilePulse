package br.com.jards.filepulse.batch.processor;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import br.com.jards.filepulse.batch.exception.RegistroInvalidoException;
import br.com.jards.filepulse.batch.model.RegistroClienteCsv;

public class CustomerItemProcessorTest {

    @Test
    void shouldAssociateCustomerWithFileProcessing() throws Exception {

        Long fileProcessingId = 42L;

        var processamento = new ClienteItemProcessor(fileProcessingId);

        var record = new RegistroClienteCsv(
                "Cliente Teste",
                "52998224725",
                "1234",
                "000001-0",
                "150.50",
                "09/26",
                null);

        var cliente = processamento.process(record);

        assertEquals(fileProcessingId, cliente.id());
    }

    @Test
    void shouldProcessValidCustomer() throws Exception {

        var processor = new ClienteItemProcessor(42L);

        var record = new RegistroClienteCsv(
                "Cliente Teste",
                "529.982.247-25",
                "1234",
                "000001-0",
                "150.50",
                "09/26",
                null);

        var cliente = processor.process(record);

        assertEquals(42L, cliente.id());
        assertEquals("Cliente Teste", cliente.nome());
        assertEquals("52998224725", cliente.cpf());
        assertEquals("1234", cliente.agencia());
        assertEquals("000001-0", cliente.conta());
        assertEquals(new BigDecimal("150.50"), cliente.valor());
        assertEquals("09/26", cliente.mesReferencia());
    }

    @Test
    void shouldRejectCustomerWithInvalidCpf() {

        var processor = new ClienteItemProcessor(42L);

        var record = new RegistroClienteCsv(
                "Cliente Teste",
                "11111111111",
                "1234",
                "000001-0",
                "150.50",
                "09/26",
                null);

        assertThrows(
                RegistroInvalidoException.class,
                () -> processor.process(record));
    }
}
