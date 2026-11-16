package sn.senwallet.simulators.mno;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import sn.senwallet.simulators.common.SimulatorCommonConfiguration;

@SpringBootApplication
@Import(SimulatorCommonConfiguration.class)
public class MnoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MnoApplication.class, args);
    }
}
