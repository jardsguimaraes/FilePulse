package br.com.jards.filepulse.batch.processor;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import br.com.jards.filepulse.batch.exception.RegistroInvalidoException;
import br.com.jards.filepulse.batch.model.RegistroClienteCsv;

class CustomerItemProcessorTest {

    private static final Long ID_PROCESSAMENTO_ARQUIVO = 42L;
    private static final Long ID_CARGA_LOGICA = 10L;
    private static final String ID_REGISTRO = "REG-000001";

    @Test
    void shouldAssociateCustomerWithFileProcessing() throws Exception {

        var processor = new ClienteItemProcessor(
                ID_PROCESSAMENTO_ARQUIVO,
                ID_CARGA_LOGICA);

        var registro = criarRegistroValido();
        var cliente = processor.process(registro);

        assertEquals(ID_PROCESSAMENTO_ARQUIVO, cliente.idProcessamentoArquivo());
        assertEquals(ID_CARGA_LOGICA, cliente.idCargaLogica());
        assertEquals(ID_REGISTRO, cliente.idRegistro());
    }

    @Test
    void shouldProcessValidCustomer() throws Exception {

        var processor = new ClienteItemProcessor(ID_PROCESSAMENTO_ARQUIVO, ID_CARGA_LOGICA);
        var registro = criarRegistroValido();
        var cliente = processor.process(registro);

        assertAll(
                () -> assertEquals(ID_PROCESSAMENTO_ARQUIVO, cliente.idProcessamentoArquivo()),
                () -> assertEquals(ID_CARGA_LOGICA, cliente.idCargaLogica()),
                () -> assertEquals(ID_REGISTRO, cliente.idRegistro()),
                () -> assertEquals("Cliente Teste", cliente.nome()),
                () -> assertEquals("52998224725", cliente.cpf()),
                () -> assertEquals("1234", cliente.agencia()),
                () -> assertEquals("000001-0", cliente.conta()),
                () -> assertEquals(new BigDecimal("150.50"), cliente.valor()),
                () -> assertEquals("09/26", cliente.mesReferencia()));
    }

    @Test
    void shouldRejectCustomerWithInvalidCpf() {

        var processor = new ClienteItemProcessor(ID_PROCESSAMENTO_ARQUIVO, ID_CARGA_LOGICA);

        var registro = new RegistroClienteCsv(
                ID_REGISTRO,
                "Cliente Teste",
                "11111111111",
                "1234",
                "000001-0",
                "150.50",
                "09/26",
                5L);

        assertThrows(RegistroInvalidoException.class, () -> processor.process(registro));
    }

    private RegistroClienteCsv criarRegistroValido() {

        return new RegistroClienteCsv(
                ID_REGISTRO,
                "Cliente Teste",
                "529.982.247-25",
                "1234",
                "000001-0",
                "150.50",
                "09/26",
                5L);
    }
}
