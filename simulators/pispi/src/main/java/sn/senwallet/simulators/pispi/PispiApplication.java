package sn.senwallet.simulators.pispi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import sn.senwallet.simulators.common.SimulatorCommonConfiguration;

@SpringBootApplication
@Import(SimulatorCommonConfiguration.class)
public class PispiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PispiApplication.class, args);
    }
}
