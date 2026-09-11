package com.skateboard.podcast.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("socialLinksExtractionCases")
    void extractsExpectedSocialLinks(String caseName, String raw, String expectedSocialLinksJson) {
        var result = parser.parse(raw);

        assertThat(result.socialMediaLinksJson()).isEqualTo(expectedSocialLinksJson);
    }

    private static Stream<Arguments> socialLinksExtractionCases() {
        return Stream.of(
                Arguments.of("shouldExtractSingleGuestInstagram", """
                        Descrição do episódio.

                        CONVIDADO: https://www.instagram.com/mauricio.carvaiho/
                        """,
                        "[{\"platform\":\"instagram\",\"url\":\"https://www.instagram.com/mauricio.carvaiho\"}]"),
                Arguments.of("shouldIgnorePresenterInstagram", """
                        Descrição.

                        CONVIDADO:
                        https://www.instagram.com/guest/

                        --------
                        Apresentado por
                        ALEX CARDOSO:
                        https://www.instagram.com/alexcardososk8/
                        """,
                        "[{\"platform\":\"instagram\",\"url\":\"https://www.instagram.com/guest\"}]"),
                Arguments.of("shouldIgnoreSponsorInstagram", """
                        Descrição.

                        Apoio:
                        PURPLE VIE AÇAI:
                        https://www.instagram.com/purplevieacai/
                        """,
                        "[]"),
                Arguments.of("shouldRemoveDuplicateInstagramUrls", """
                        Descrição.

                        CONVIDADOS:
                        https://www.instagram.com/user1/
                        https://www.instagram.com/user1/?igsh=abc
                        """,
                        "[{\"platform\":\"instagram\",\"url\":\"https://www.instagram.com/user1\"}]"));
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingOrPlainDescriptionCases")
    void handlesMissingOrPlainDescriptions(String caseName, String raw, String expectedDescription) {
        var result = parser.parse(raw);

        assertThat(result.description()).isEqualTo(expectedDescription);
        assertThat(result.socialMediaLinksJson()).isEqualTo("[]");
    }

    private static Stream<Arguments> missingOrPlainDescriptionCases() {
        return Stream.of(
                Arguments.of("shouldHandleNullDescription", null, ""),
                Arguments.of("shouldHandleBlankDescription", "   ", ""),
                Arguments.of("shouldHandleDescriptionWithoutMetadata",
                        "Um episódio especial sobre a história do skate em Barcelona.",
                        "Um episódio especial sobre a história do skate em Barcelona."));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caseInsensitiveAndNormalizationCases")
    void handlesCaseInsensitiveHeadersAndUrlNormalization(String caseName, String raw, String expectedSocialLinksJson) {
        var result = parser.parse(raw);

        assertThat(result.socialMediaLinksJson()).isEqualTo(expectedSocialLinksJson);
    }

    private static Stream<Arguments> caseInsensitiveAndNormalizationCases() {
        return Stream.of(
                Arguments.of("shouldHandleConvidadoCaseInsensitive",
                        "Descrição.\n\nconvidado: https://www.instagram.com/mauricio.carvaiho/",
                        "[{\"platform\":\"instagram\",\"url\":\"https://www.instagram.com/mauricio.carvaiho\"}]"),
                Arguments.of("shouldHandleConvidadosCaseInsensitive",
                        "Descrição.\n\nConvidados: https://www.instagram.com/user1/",
                        "[{\"platform\":\"instagram\",\"url\":\"https://www.instagram.com/user1\"}]"),
                Arguments.of("shouldNormalizeInstagramTrackingParameters",
                        "Descrição.\n\nCONVIDADO: https://www.instagram.com/example/?igsh=abc123",
                        "[{\"platform\":\"instagram\",\"url\":\"https://www.instagram.com/example\"}]"));
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
