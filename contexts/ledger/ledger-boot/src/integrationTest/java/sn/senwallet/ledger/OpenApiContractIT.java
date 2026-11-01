package sn.senwallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** contracts/openapi/ledger.yaml is valid and lists exactly the operations the controllers serve. */
class OpenApiContractIT extends LedgerIntegrationTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    private OpenAPI spec;

    @BeforeEach
    void parseSpec() {
        var options = new ParseOptions();
        options.setResolve(true);
        var location = Path.of(System.getProperty("senwallet.root"), "contracts", "openapi", "ledger.yaml");
        var result = new OpenAPIV3Parser().readLocation(location.toUri().toString(), null, options);
        assertThat(result.getMessages()).as("spec validation messages").isEmpty();
        spec = result.getOpenAPI();
    }

    @Test
    void specAndControllersDescribeTheSameOperations() {
        assertThat(specOperations()).isEqualTo(controllerOperations());
    }

    private Set<String> controllerOperations() {
        var operations = new TreeSet<String>();
        mappings.getHandlerMethods().forEach((info, method) -> {
            if (!method.getBeanType().getPackageName().startsWith("sn.senwallet.ledger")) {
                return;
            }
            for (var path : info.getPatternValues()) {
                for (var verb : info.getMethodsCondition().getMethods()) {
                    operations.add(verb.name() + " " + path);
                }
            }
        });
        return operations;
    }

    private Set<String> specOperations() {
        var operations = new TreeSet<String>();
        spec.getPaths().forEach((path, item) -> item.readOperationsMap().keySet()
                .forEach(verb -> operations.add(verb.name().toUpperCase(Locale.ROOT) + " " + path)));
        return operations;
    }
}
