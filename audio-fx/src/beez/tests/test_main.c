#include "test_framework.h"

extern void run_oscillator_tests(void);
extern void run_envelope_tests(void);
extern void run_channel_tests(void);
extern void run_synth_engine_tests(void);
extern void run_ring_buffer_tests(void);
extern void run_audio_utils_tests(void);

int main(int argc, char* argv[]) {
    (void)argc;
    (void)argv;
    
    printf("========================================\n");
    printf("       Beez Audio Core Test Suite      \n");
    printf("========================================\n");
    
    run_oscillator_tests();
    run_envelope_tests();
    run_channel_tests();
    run_synth_engine_tests();
    run_ring_buffer_tests();
    run_audio_utils_tests();
    
    test_print_summary();
    
    return test_get_exit_code();
}
