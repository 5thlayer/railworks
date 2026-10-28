// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Locomotives stand only at a Train's ends: a car couples onto an end of a Train at rest only where
 * nothing but Locomotives would lie between a Locomotive and an end. A makeup is written front to
 * back, L a Locomotive and C a Cargo car.
 */
class TrainMakeupTest {

    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(40, 5, 0);

    private final RailNetwork network = new RailNetwork();

    TrainMakeupTest() {
        network.add(Span.of(A, B, 40));
    }

    private static Car car(char kind) {
        return kind == 'L' ? Locomotive.burner() : CargoCar.create();
    }

    private Train train(String makeup) {
        List<Car> cars = new ArrayList<>();
        for (char kind : makeup.toCharArray()) {
            cars.add(car(kind));
        }
        return Train.of(new Place(network.get(A, B), B, 20.0), cars);
    }

    /** Whether the last car of {@code makeup}, or its first, couples onto a Train of the rest. */
    private boolean couples(String makeup, Train.End end) {
        if (end == Train.End.BACK) {
            return train(makeup.substring(0, makeup.length() - 1))
                    .takes(end, car(makeup.charAt(makeup.length() - 1)));
        }
        return train(makeup.substring(1)).takes(end, car(makeup.charAt(0)));
    }

    @ParameterizedTest(name = "{0} at the {1}: {2}")
    @CsvSource({
            "LC, FRONT, true", "LC, BACK, true", "CL, FRONT, true", "CL, BACK, true",
            "LL, FRONT, true", "CC, BACK, true",
            "LCL, FRONT, true", "LCL, BACK, true",
            "LLC, FRONT, true", "LLC, BACK, true",
            "LLCCL, FRONT, true", "LLCCL, BACK, true",
            "CLC, FRONT, false", "CLC, BACK, false",
            "LCLC, FRONT, false", "LCLC, BACK, false",
            "CLLC, FRONT, false", "CLLC, BACK, false",
    })
    void aCarCouplesOnlyWhereLocomotivesStayAtTheEnds(String makeup, Train.End end, boolean couples) {
        assertEquals(couples, couples(makeup, end));
    }
}
