package xyz.volcanobay.empiric.api.network;

public interface NetworkListener<N extends NetworkModule> {

    default void merged(N kept, N absorbed) {
    }

    default void split(N original, N split) {
    }

    default void updated(N network) {
    }
}
