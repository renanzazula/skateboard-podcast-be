package com.skateboard.podcast.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YoutubeDescriptionParserTest {

    private YoutubeDescriptionParser parser;

    @BeforeEach
    void setUp() {
        parser = new YoutubeDescriptionParser(new ObjectMapper());
    }

    @Test
    void shouldExtractDescriptionBeforeSupportSection() {
        String raw = """
                As histórias por trás do skate na Europa.
                Mauricio Carvalho, skatista dos anos 2000.

                APOIE NOSSO CANAL:https://buymeacoffee.com/skateboardpodcast
                --------
                CONVIDADO: https://www.instagram.com/mauricio.carvaiho/
                """;

        var result = parser.parse(raw);

        assertThat(result.description()).isEqualTo("""
                As histórias por trás do skate na Europa.
                Mauricio Carvalho, skatista dos anos 2000.""");
    }

    @Test
    void shouldExtractSingleGuestInstagram() {
        String raw = """
                Descrição do episódio.

                CONVIDADO: https://www.instagram.com/mauricio.carvaiho/
                """;

        var result = parser.parse(raw);

        assertThat(result.socialMediaLinksJson())
                .isEqualTo("[{\"platform\":\"instagram\",\"url\":\"https://www.instagram.com/mauricio.carvaiho\"}]");
    }

    @Test
    void shouldExtractMultipleGuestInstagrams() {
        String raw = """
                Descrição.

                CONVIDADOS:
                https://www.instagram.com/user1/
                https://www.instagram.com/user2/
                """;

        var result = parser.parse(raw);

        assertThat(result.socialMediaLinksJson()).isEqualTo(
                "[{\"platform\":\"instagram\",\"url\":\"https://www.instagram.com/user1\"},"
                        + "{\"platform\":\"instagram\",\"url\":\"https://www.instagram.com/user2\"}]");
    }

    @Test
    void shouldIgnorePresenterInstagram() {
        String raw = """
                Descrição.

                CONVIDADO:
                https://www.instagram.com/guest/

                --------
                Apresentado por
                ALEX CARDOSO:
                https://www.instagram.com/alexcardososk8/
                """;

        var result = parser.parse(raw);

        assertThat(result.socialMediaLinksJson())
                .isEqualTo("[{\"platform\":\"instagram\",\"url\":\"https://www.instagram.com/guest\"}]");
    }

    @Test
    void shouldIgnoreSponsorInstagram() {
        String raw = """
                Descrição.

                Apoio:
                PURPLE VIE AÇAI:
                https://www.instagram.com/purplevieacai/
                """;

        var result = parser.parse(raw);

        assertThat(result.socialMediaLinksJson()).isEqualTo("[]");
    }

    @Test
    void shouldReturnEmptySocialLinksWhenGuestSectionIsMissing() {
        String raw = """
                Descrição do episódio.

                APOIE NOSSO CANAL: ...
                --------
                Apresentado por
                ...
                """;

        var result = parser.parse(raw);

        assertThat(result.description()).isEqualTo("Descrição do episódio.");
        assertThat(result.socialMediaLinksJson()).isEqualTo("[]");
    }

    @Test
    void shouldHandleNullDescription() {
        var result = parser.parse(null);

        assertThat(result.description()).isEqualTo("");
        assertThat(result.socialMediaLinksJson()).isEqualTo("[]");
    }

    @Test
    void shouldHandleBlankDescription() {
        var result = parser.parse("   ");

        assertThat(result.description()).isEqualTo("");
        assertThat(result.socialMediaLinksJson()).isEqualTo("[]");
    }

    @Test
    void shouldHandleDescriptionWithoutMetadata() {
        var result = parser.parse("Um episódio especial sobre a história do skate em Barcelona.");

        assertThat(result.description()).isEqualTo("Um episódio especial sobre a história do skate em Barcelona.");
        assertThat(result.socialMediaLinksJson()).isEqualTo("[]");
    }

    @Test
    void shouldHandleConvidadoCaseInsensitive() {
        String raw = "Descrição.\n\nconvidado: https://www.instagram.com/mauricio.carvaiho/";

        var result = parser.parse(raw);

        assertThat(result.socialMediaLinksJson())
                .isEqualTo("[{\"platform\":\"instagram\",\"url\":\"https://www.instagram.com/mauricio.carvaiho\"}]");
    }

    @Test
    void shouldHandleConvidadosCaseInsensitive() {
        String raw = "Descrição.\n\nConvidados: https://www.instagram.com/user1/";

        var result = parser.parse(raw);

        assertThat(result.socialMediaLinksJson())
                .isEqualTo("[{\"platform\":\"instagram\",\"url\":\"https://www.instagram.com/user1\"}]");
    }

    @Test
    void shouldRemoveDuplicateInstagramUrls() {
        String raw = """
                Descrição.

                CONVIDADOS:
                https://www.instagram.com/user1/
                https://www.instagram.com/user1/?igsh=abc
                """;

        var result = parser.parse(raw);

        assertThat(result.socialMediaLinksJson())
                .isEqualTo("[{\"platform\":\"instagram\",\"url\":\"https://www.instagram.com/user1\"}]");
    }

    @Test
    void shouldNormalizeInstagramTrackingParameters() {
        String raw = "Descrição.\n\nCONVIDADO: https://www.instagram.com/example/?igsh=abc123";

        var result = parser.parse(raw);

        assertThat(result.socialMediaLinksJson())
                .isEqualTo("[{\"platform\":\"instagram\",\"url\":\"https://www.instagram.com/example\"}]");
    }

    @Test
    void shouldPreserveDescriptionParagraphs() {
        String raw = """
                Primeiro parágrafo.

                Segundo parágrafo.

                APOIE NOSSO CANAL: ...
                """;

        var result = parser.parse(raw);

        assertThat(result.description()).isEqualTo("Primeiro parágrafo.\n\nSegundo parágrafo.");
    }

    @Test
    void shouldNotTreatMetadataWordInsideBodyTextAsHeader() {
        String raw = "Neste episódio recebemos um convidado: alguém muito especial. Confiram!";

        var result = parser.parse(raw);

        assertThat(result.description()).isEqualTo(raw);
        assertThat(result.socialMediaLinksJson()).isEqualTo("[]");
    }
}
