package br.com.jards.filepulse.batch.processor;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import br.com.jards.filepulse.batch.exception.InvalidRecordException;
import br.com.jards.filepulse.batch.model.CustomerCsvRecord;

public class CustomerItemProcessorTest {

    @Test
    void shouldAssociateCustomerWithFileProcessing() throws Exception {

        Long fileProcessingId = 42L;

        var processor = new CustomerItemProcessor(fileProcessingId);

        var record = new CustomerCsvRecord(
                "Cliente Teste",
                "52998224725",
                "1234",
                "000001-0",
                "150.50",
                "09/26");

        var customer = processor.process(record);

        assertEquals(fileProcessingId, customer.fileProcessingId());
    }

    @Test
    void shouldProcessValidCustomer() throws Exception {

        var processor = new CustomerItemProcessor(42L);

        var record = new CustomerCsvRecord(
                "Cliente Teste",
                "529.982.247-25",
                "1234",
                "000001-0",
                "150.50",
                "09/26");

        var customer = processor.process(record);

        assertEquals(42L, customer.fileProcessingId());
        assertEquals("Cliente Teste", customer.name());
        assertEquals("52998224725", customer.cpf());
        assertEquals("1234", customer.agency());
        assertEquals("000001-0", customer.account());
        assertEquals(new BigDecimal("150.50"), customer.value());
        assertEquals("09/26", customer.referenceMonth());
    }

    @Test
    void shouldRejectCustomerWithInvalidCpf() {

        var processor = new CustomerItemProcessor(42L);

        var record = new CustomerCsvRecord(
                "Cliente Teste",
                "11111111111",
                "1234",
                "000001-0",
                "150.50",
                "09/26");

        assertThrows(
                InvalidRecordException.class,
                () -> processor.process(record));
    }
}
