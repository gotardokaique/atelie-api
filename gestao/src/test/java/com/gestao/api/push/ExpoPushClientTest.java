package com.gestao.api.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ExpoPushClientTest {

    private static final String TOKEN = "ExponentPushToken[abc]";

    @Test
    void semAccessTokenFalhaSemChamarARede() {
        ExpoPushClient client = new ExpoPushClient("");

        List<PushTicket> tickets = client.enviar(List.of(new PushMessage(TOKEN, "t", "b", Map.of(), "prazos")));

        assertThat(tickets).singleElement().satisfies(t -> {
            assertThat(t.isOk()).isFalse();
            assertThat(t.erro()).isEqualTo(ExpoPushClient.ERRO_SEM_TOKEN);
        });
        assertThat(client.consultarRecibos(List.of("x"))).isEmpty();
    }

    @Test
    void ticketOkEDeviceNotRegistered() {
        PushTicket ok = ExpoPushClient.lerTicket(TOKEN, Map.of("status", "ok", "id", "XXXX"));
        PushTicket invalido = ExpoPushClient.lerTicket(TOKEN, Map.of(
                "status", "error",
                "message", "not registered",
                "details", Map.of("error", "DeviceNotRegistered")));

        assertThat(ok.isOk()).isTrue();
        assertThat(ok.id()).isEqualTo("XXXX");
        assertThat(invalido.isOk()).isFalse();
        assertThat(invalido.isDispositivoInvalido()).isTrue();
        assertThat(invalido.token()).isEqualTo(TOKEN);
    }

    @Test
    void reciboComErro() {
        PushReceipt ok = ExpoPushClient.lerRecibo("1", Map.of("status", "ok"));
        PushReceipt invalido = ExpoPushClient.lerRecibo("2", Map.of(
                "status", "error", "details", Map.of("error", "DeviceNotRegistered")));

        assertThat(ok.isOk()).isTrue();
        assertThat(invalido.isDispositivoInvalido()).isTrue();
    }
}
