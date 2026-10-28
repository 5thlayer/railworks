// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The Electric Locomotive's drive (#9): a Train on a Line of two 20-block Spans, with round numbers
 * for its drive, its Electric Locomotives' charge held by a fake {@link Goods}, as the level's car
 * records hold it, and spent through {@link Locomotive#power}.
 */
class ElectricLocomotiveTest {

    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(20, 5, 0);
    private static final Arm C = new Arm(40, 5, 0);
    private static final double EPS = 1e-9;

    private final RailNetwork network = new RailNetwork();
    private final Charges goods = new Charges();

    @BeforeEach
    void line() {
        // 1 block/tick at most, +0.1 a tick up, -0.25 a tick braking, -0.01 a tick coasting.
        LocomotiveSpec.configure(20.0, 40.0, 100.0, 4.0);
        network.add(Span.of(A, B, 20));
        network.add(Span.of(B, C, 20));
    }

    @AfterEach
    void reset() {
        LocomotiveSpec.configure(LocomotiveSpec.DEFAULT_MAX_SPEED, LocomotiveSpec.DEFAULT_ACCELERATION,
                LocomotiveSpec.DEFAULT_BRAKING, LocomotiveSpec.DEFAULT_FRICTION);
        ChargeSpec.reset();
    }

    /** At the A end of the first Span, facing C, at rest. */
    private Place atStart() {
        return new Place(network.get(A, B), B, 20.0);
    }

    private Locomotive electric(long charge) {
        Locomotive locomotive = Locomotive.electric();
        goods.charges.put(locomotive.id(), charge);
        return locomotive;
    }

    /** {@code times} ticks pressing forward from the first car, a Burner lighting nothing. */
    private void forward(Train train, int times) {
        for (int i = 0; i < times; i++) {
            train.tick(network, Throttle.FORWARD, Locomotive.power(goods, locomotive -> 0L));
        }
    }

    @Test
    void atFullPowerItDrawsThreeHundredFeATick() {
        Locomotive locomotive = electric(1_000_000L);
        Train train = Train.of(atStart(), locomotive);
        forward(train, 10);
        assertEquals(1_000_000L - 10 * 300L, goods.charge(locomotive));
        assertEquals(1.0, train.speed(), EPS);
    }

    @Test
    void theFeToJouleRateSetsHowMuchATickDraws() {
        ChargeSpec.configure(50L, ChargeSpec.DEFAULT_LOCOMOTIVE_CAPACITY, ChargeSpec.DEFAULT_CHARGER_RATE,
                ChargeSpec.DEFAULT_CHARGER_BUFFER, ChargeSpec.DEFAULT_BURNER_GENERATOR_KILOWATTS);
        Locomotive locomotive = electric(1_000_000L);
        forward(Train.of(atStart(), locomotive), 10);
        assertEquals(1_000_000L - 10 * 600L, goods.charge(locomotive));
    }

    @Test
    void aRateThatSplitsATickDrawsWholeFeAndKeepsTheSliverForTheNext() {
        // 30,000 J a tick at 70 J an FE is 428.57 FE: 429 the first tick, and the 30 J over carried on.
        ChargeSpec.configure(70L, ChargeSpec.DEFAULT_LOCOMOTIVE_CAPACITY, ChargeSpec.DEFAULT_CHARGER_RATE,
                ChargeSpec.DEFAULT_CHARGER_BUFFER, ChargeSpec.DEFAULT_BURNER_GENERATOR_KILOWATTS);
        Locomotive locomotive = electric(1_000_000L);
        forward(Train.of(atStart(), locomotive), 7);
        long spent = 1_000_000L - goods.charge(locomotive);
        // Seven ticks are 210,000 J, 3,000 FE exactly: no joule lost or made.
        assertEquals(3_000L, spent);
        assertEquals(0L, locomotive.fuel().storedJoules());
    }

    @Test
    void outOfChargeItPullsNothingAndBrakesDown() {
        Locomotive locomotive = electric(600L);
        Train train = Train.of(atStart(), locomotive);
        forward(train, 2);
        assertEquals(0.2, train.speed(), EPS);
        assertEquals(0L, goods.charge(locomotive));
        forward(train, 1);
        // Braked at 0.25 a tick, from 0.2 to a stop.
        assertEquals(0.0, train.speed(), EPS);
    }

    @Test
    void anEmptyElectricLocomotiveIsOnlyMassToTheBurnerPullingIt() {
        Locomotive burner = Locomotive.burner();
        burner.fuel().light(1_000_000_000L);
        Train train = Train.of(atStart(), List.of(burner, electric(0L)));
        forward(train, 1);
        // One Locomotive's pull over two Locomotives' mass.
        assertEquals(0.05, train.speed(), EPS);
    }

    @Test
    void aMixedTrainHasEachLocomotivePayFromItsOwnFuelOrCharge() {
        Locomotive burner = Locomotive.burner();
        burner.fuel().light(1_000_000L);
        Locomotive electric = electric(1_000_000L);
        Train train = Train.of(atStart(), List.of(burner, electric));
        forward(train, 3);
        assertEquals(1_000_000L - 3 * LocomotiveSpec.JOULES_PER_TICK_AT_FULL_POWER, burner.fuel().storedJoules());
        assertEquals(1_000_000L - 3 * 300L, goods.charge(electric));
        assertEquals(0L, electric.fuel().storedJoules(), "an Electric draws a tick at a time, banking nothing");
        assertEquals(0.3, train.speed(), EPS, "both pull: the acceleration of one alone");
    }

    @Test
    void itPullsAWayOnlyWhileALocomotiveFacingThatWayHoldsFuelOrCharge() {
        Locomotive east = electric(0L);
        Locomotive west = electric(300L);
        // Midway along the first Span, room behind it for the car coupled there.
        Train train = Train.of(new Place(network.get(A, B), B, 10.0), List.of(east, CargoCar.create()));
        train = train.coupled(network, Train.End.BACK, west, true);
        assertFalse(train.pulls(1, Locomotive.waiting(goods)), "the one facing its heading is empty");
        assertTrue(train.pulls(-1, Locomotive.waiting(goods)), "the one facing back holds a tick");
        goods.charges.put(west.id(), 299L);
        assertFalse(train.pulls(-1, Locomotive.waiting(goods)), "short of a tick is no charge to pull with");
        goods.charges.put(east.id(), 300L);
        assertTrue(train.pulls(1, Locomotive.waiting(goods)));
    }

    @Test
    void anAbstractTrainKeepsItsChargeThroughASaveAndSpendsItFromItsRecord() {
        Locomotive locomotive = electric(1_000_000L);
        Train train = Train.of(atStart(), List.of(locomotive, CargoCar.create()));
        var saved = Train.CODEC.encodeStart(JsonOps.INSTANCE, train).getOrThrow();
        Train loaded = Train.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        Locomotive again = (Locomotive) loaded.cars().getFirst();
        assertTrue(again.isElectric(), "it loads as an Electric Locomotive");
        assertEquals(locomotive.id(), again.id());
        // Its charge is its record's, by its id: there whether it has a view or not.
        assertEquals(1_000_000L, goods.charge(again));
        forward(loaded, 5);
        assertEquals(1_000_000L - 5 * 300L, goods.charge(again));
    }

    /** An Electric Locomotive's charge by its id, as the level's car records hold it; nothing else. */
    static final class Charges implements Goods<String> {

        final Map<UUID, Long> charges = new HashMap<>();

        @Override
        public long charge(Locomotive locomotive) {
            return locomotive.isElectric() ? charges.getOrDefault(locomotive.id(), 0L) : 0L;
        }

        @Override
        public boolean spendCharge(Locomotive locomotive, long fe) {
            long held = charge(locomotive);
            if (held < fe) {
                return false;
            }
            charges.put(locomotive.id(), held - fe);
            return true;
        }

        @Override
        public long giveCharge(Logistics.Charger from, Locomotive into, long most) {
            return 0L;
        }

        @Override
        public int stackSize(String item) {
            return 64;
        }

        @Override
        public long held(Logistics.Hatch<String> hatch, String item) {
            return 0L;
        }

        @Override
        public long held(GoodsCar car, String item) {
            return 0L;
        }

        @Override
        public Map<String, Long> contents(GoodsCar car) {
            return Map.of();
        }

        @Override
        public boolean empty(GoodsCar car) {
            return true;
        }

        @Override
        public int slots(CargoCar car) {
            return 27;
        }

        @Override
        public long tank(FluidCar car) {
            return 0L;
        }

        @Override
        public long load(Logistics.Hatch<String> from, GoodsCar into, String item, long most) {
            return 0L;
        }

        @Override
        public long unload(GoodsCar from, Logistics.Hatch<String> into, String item, long most) {
            return 0L;
        }

        @Override
        public Map<String, Long> contents(Logistics.Hatch<String> hatch) {
            return Map.of();
        }

        @Override
        public long fuelRoom(Logistics.Hatch<String> from, Locomotive locomotive, String item) {
            return 0L;
        }

        @Override
        public long refuel(Logistics.Hatch<String> from, Locomotive into, String item, long most) {
            return 0L;
        }

        @Override
        public boolean slotsFuelled(Locomotive locomotive) {
            return false;
        }
    }
}
