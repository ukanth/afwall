/*
 * libxt_LED.c - shared library add-on to iptables to add customized LED
 *               trigger support.
 *
 * (C) 2008 Adam Nielsen <a.nielsen@shikadi.net>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 */
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <getopt.h>
#include <stddef.h>

#include <xtables.h>

#include <linux/netfilter/xt_LED.h>

static const struct option LED_opts[] = {
	{.name = "led-trigger-id",   .has_arg = true,  .val = 'i'},
	{.name = "led-delay",        .has_arg = true,  .val = 'd'},
	{.name = "led-always-blink", .has_arg = false, .val = 'a'},
	XT_GETOPT_TABLEEND,
};

static void LED_help(void)
{
	printf(
"LED target options:\n"
"--led-trigger-id name           suffix for led trigger name\n"
"--led-delay ms                  leave the LED on for this number of\n"
"                                milliseconds after triggering.\n"
"--led-always-blink              blink on arriving packets, even if\n"
"                                the LED is already on.\n"
	);
}

static int LED_parse(int c, char **argv, int invert, unsigned int *flags,
		     const void *entry, struct xt_entry_target **target)
{
	struct xt_led_info *led = (void *)(*target)->data;

	switch (c) {
	case 'i':
		xtables_param_act(XTF_NO_INVERT, "LED",
			"--led-trigger-id", invert);
		if (strlen("netfilter-") + strlen(optarg) > sizeof(led->id))
			xtables_error(PARAMETER_PROBLEM,
				"--led-trigger-id must be 16 chars or less");
		if (optarg[0] == '\0')
			xtables_error(PARAMETER_PROBLEM,
				"--led-trigger-id cannot be blank");

		/* "netfilter-" + 16 char id == 26 == sizeof(led->id) */
		strcpy(led->id, "netfilter-");
		strcat(led->id, optarg);
		*flags = 1;
		return true;

	case 'd':
		xtables_param_act(XTF_NO_INVERT, "LED", "--led-delay", invert);
		if (strncasecmp(optarg, "inf", 3) == 0)
			led->delay = -1;
		else
			led->delay = strtoul(optarg, NULL, 0);

		return true;

	case 'a':
		if (!invert)
			led->always_blink = 1;
		return true;
	}
	return false;
}

static void LED_final_check(unsigned int flags)
{
	if (flags == 0)
		xtables_error(PARAMETER_PROBLEM,
			"--led-trigger-id must be specified");
}

static void LED_print(const void *ip, const struct xt_entry_target *target,
		      int numeric)
{
	const struct xt_led_info *led = (void *)target->data;
	const char *id = led->id + strlen("netfilter-"); /* trim off prefix */

	printf("led-trigger-id:\"");
	/* Escape double quotes and backslashes in the ID */
	while (*id != '\0') {
		if (*id == '"' || *id == '\\')
			printf("\\");
		printf("%c", *id++);
	}
	printf("\" ");

	if (led->delay == -1)
		printf("led-delay:inf ");
	else
		printf("led-delay:%dms ", led->delay);

	if (led->always_blink)
		printf("led-always-blink ");
}

static void LED_save(const void *ip, const struct xt_entry_target *target)
{
	const struct xt_led_info *led = (void *)target->data;
	const char *id = led->id + strlen("netfilter-"); /* trim off prefix */

	printf("--led-trigger-id \"");
	/* Escape double quotes and backslashes in the ID */
	while (*id != '\0') {
		if (*id == '"' || *id == '\\')
			printf("\\");
		printf("%c", *id++);
	}
	printf("\" ");

	/* Only print the delay if it's not zero (the default) */
	if (led->delay > 0)
		printf("--led-delay %d ", led->delay);
	else if (led->delay == -1)
		printf("--led-delay inf ");

	/* Only print always_blink if it's not set to the default */
	if (led->always_blink)
		printf("--led-always-blink ");
}

static struct xtables_target led_tg_reg = {
	.version       = XTABLES_VERSION,
	.name          = "LED",
	.family        = PF_UNSPEC,
	.revision      = 0,
	.size          = XT_ALIGN(sizeof(struct xt_led_info)),
	.userspacesize = offsetof(struct xt_led_info, internal_data),
	.help          = LED_help,
	.parse         = LED_parse,
	.final_check   = LED_final_check,
	.extra_opts    = LED_opts,
	.print         = LED_print,
	.save          = LED_save,
};

void libxt_LED_init(void)
{
	xtables_register_target(&led_tg_reg);
}
