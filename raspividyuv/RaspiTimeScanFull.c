/*
Copyright (c) 2018, Raspberry Pi (Trading) Ltd.
Copyright (c) 2014, DSP Group Ltd.
Copyright (c) 2014, James Hughes
Copyright (c) 2013, Broadcom Europe Ltd.

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the copyright holder nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

/**
 * \file RaspiVidYUV.c
 * Command line program to capture a camera video stream and save file
 * as uncompressed YUV420 data
 * Also optionally display a preview/viewfinder of current camera input.
 *
 * Description
 *
  * 2 components are created; camera and preview.
 * Camera component has three ports, preview, video and stills.
 * Preview is connected using standard mmal connections, the video output
 * is written straight to the file in YUV 420 format via the requisite buffer
 * callback. Still port is not used
 *
 * We use the RaspiCamControl code to handle the specific camera settings.
 * We use the RaspiPreview code to handle the generic preview
*/

// We use some GNU extensions (basename)


// build/bin/raspividyuv -w 576 -h 576 -hf -md 6 -fps 90 -ex sports -br 52 -p 400,0,400,400 -t 0 -o testoutput
// build/bin/raspividyuv -w 576 -h 576 -vf -md 6 -fps 90 -ex sports -drc high -p 640,0,640,640 -t 0



#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <memory.h>
#include <sysexits.h>

#include <sys/types.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include "bcm_host.h"
#include "interface/vcos/vcos.h"

#include "interface/mmal/mmal.h"
#include "interface/mmal/mmal_logging.h"
#include "interface/mmal/mmal_buffer.h"
#include "interface/mmal/util/mmal_util.h"
#include "interface/mmal/util/mmal_util_params.h"
#include "interface/mmal/util/mmal_default_components.h"
#include "interface/mmal/util/mmal_connection.h"
#include "interface/mmal/util/mmal_component_wrapper.h"

#include "interface/mmal/core/mmal_buffer_private.h"
#include "interface/mmal/core/mmal_queue.c"


#include "RaspiCommonSettings.h"
#include "RaspiCamControl.h"
#include "RaspiPreview.h"
#include "RaspiCLI.h"
#include "RaspiHelpers.h"
#include "RaspiGPS.h"

#include <semaphore.h>

// Standard port setting for the camera component
#define MMAL_CAMERA_PREVIEW_PORT 0
#define MMAL_CAMERA_VIDEO_PORT 1
#define MMAL_CAMERA_CAPTURE_PORT 2

// Video format information
// 0 implies variable
#define VIDEO_FRAME_RATE_NUM 30
#define VIDEO_FRAME_RATE_DEN 1

/// Video render needs at least 2 buffers.
//  here it is the resolution of pixel depth of the time map
#define VIDEO_OUTPUT_BUFFERS_NUM 256+10
#define STILL_OUTPUT_BUFFERS_NUM 3
#define RENDER_OUTPUT_BUFFERS_NUM 64

/// Interval at which we check for an failure abort during capture
const int ABORT_INTERVAL = 100; // ms

const int pattern[8][8] = {
          { 0, 32,  8, 40,  2, 34, 10, 42},   /* 8x8 Bayer ordered dithering  */
          {48, 16, 56, 24, 50, 18, 58, 26},   /* pattern.  Each input pixel   */
          {12, 44,  4, 36, 14, 46,  6, 38},   /* is scaled to the 0..63 range */
          {60, 28, 52, 20, 62, 30, 54, 22},   /* before looking in this table */
          { 3, 35, 11, 43,  1, 33,  9, 41},   /* to determine the action.     */
          {51, 19, 59, 27, 49, 17, 57, 25},
          {15, 47,  7, 39, 13, 45,  5, 37},
          {63, 31, 55, 23, 61, 29, 53, 21} };

// Forward
typedef struct RASPIVIDYUV_STATE_S RASPIVIDYUV_STATE;

/** Struct used to pass information in camera video port userdata to callback
 */
typedef struct
{
   FILE *file_handle;                   /// File handle to write buffer data to.
   RASPIVIDYUV_STATE *pstate;           /// pointer to our state in case required in callback
   int abort;                           /// Set to 1 in callback if an error occurs to attempt to abort the capture
   FILE *pts_file_handle;               /// File timestamps
   int frame;
   int64_t starttime;
   int64_t lasttime;
} PORT_USERDATA;

/** Structure containing all state information for the current run
 */
struct RASPIVIDYUV_STATE_S
{
   RASPICOMMONSETTINGS_PARAMETERS common_settings;
   int timeout;                        /// Time taken before frame is grabbed and app then shuts down. Units are milliseconds
   int framerate;                      /// Requested frame rate (fps)

   RASPIPREVIEW_PARAMETERS preview_parameters;   /// Preview setup parameters
   RASPICAM_CAMERA_PARAMETERS camera_parameters; /// Camera setup parameters

   MMAL_COMPONENT_T *camera_component;    /// Pointer to the camera component
   MMAL_COMPONENT_T *render_component;    /// Pointer to the render component
   MMAL_COMPONENT_T *decoder_component;    /// Pointer to the decoder component
   MMAL_WRAPPER_T *encoder_component;   /// Pointer to the encoder component

   MMAL_CONNECTION_T *preview_connection; /// Pointer to the connection from camera to preview

   MMAL_POOL_T *camera_pool;            /// Pointer to the pool of buffers used by camera video port
   MMAL_POOL_T *render_pool;               // Pointer to the pool of buffers used by render
   MMAL_PORT_T *render_input_port;

   MMAL_QUEUE_T *decoder_queue;               // Pointer to the queue of buffers used by render
   MMAL_BUFFER_HEADER_T *decoded_gradient_buffer;


   MMAL_FOURCC_T encoding;             /// Encoding to use for the output file.
   MMAL_PARAM_THUMBNAIL_CONFIG_T thumbnailConfig;
   int quality;                        /// JPEG quality setting (1-100)
   int restart_interval;               /// JPEG restart interval. 0 for none.

   PORT_USERDATA callback_data;         /// Used to move data to the camera callback

   int bCapturing;                      /// State of capture/pause
   int fileWritten;
   int timeScanRendering;
   uint64_t starttime;
   uint64_t lasttime;

   unsigned int scannedframe;
   unsigned int lastscannedframe;

   int test;
};

/// Command ID's and Structure defining our command line options
enum
{
   CommandTimeout,
   CommandFramerate,
};

static COMMAND_LIST cmdline_commands[] =
{
   { CommandTimeout,       "-timeout",    "t",  "Time (in ms) to capture for. If not specified, set to 5s. Zero to disable", 1 },
   { CommandFramerate,     "-framerate",  "fps","Specify the frames per second to record", 1},
};

static int cmdline_commands_size = sizeof(cmdline_commands) / sizeof(cmdline_commands[0]);


// signal globals
static int signal_USR1 = 0;
static int signal_USR2 = 0;


/**
 * Assign a default set of parameters to the state passed in
 *
 * @param state Pointer to state structure to assign defaults to
 */
static void default_status(RASPIVIDYUV_STATE *state)
{
   if (!state)
   {
      vcos_assert(0);
      return;
   }

   // Default everything to zero
   memset(state, 0, sizeof(RASPIVIDYUV_STATE));

   raspicommonsettings_set_defaults(&state->common_settings);

   // Now set anything non-zero
   state->timeout = -1; // replaced with 5000ms later if unset
   state->common_settings.width = 1920;       // Default to 1080p
   state->common_settings.height = 1080;
   state->framerate = VIDEO_FRAME_RATE_NUM;
   state->timeScanRendering = 1;
   state->scannedframe=0;
   state->encoding = MMAL_ENCODING_JPEG;
   state->quality = 85;
   state->thumbnailConfig.enable = 1;
   state->thumbnailConfig.width = 64;
   state->thumbnailConfig.height = 48;
   state->thumbnailConfig.quality = 35;
   state->restart_interval = 0;
   state->fileWritten = 0;

   // Setup preview window defaults
   raspipreview_set_defaults(&state->preview_parameters);

   // Set up the camera_parameters to default
   raspicamcontrol_set_defaults(&state->camera_parameters);
}


/**
 * Display usage information for the application to stdout
 *
 * @param app_name String to display as the application name
 */
static void application_help_message(char *app_name)
{
   fprintf(stdout, "Display camera output to display, and optionally saves an uncompressed YUV420 or RGB file \n\n");
   fprintf(stdout, "NOTE: High resolutions and/or frame rates may exceed the bandwidth of the system due\n");
   fprintf(stdout, "to the large amounts of data being moved to the SD card. This will result in undefined\n");
   fprintf(stdout, "results in the subsequent file.\n");
   fprintf(stdout, "The single raw file produced contains all the images. Each image in the files will be of size\n");
   fprintf(stdout, "width*height*1.5 for YUV or width*height*3 for RGB, unless width and/or height are not divisible by 16.");
   fprintf(stdout, "Use the image size displayed during the run (in verbose mode) for an accurate value\n");

   fprintf(stdout, "The Linux split command can be used to split up the file to individual frames\n");

   fprintf(stdout, "\nusage: %s [options]\n\n", app_name);

   fprintf(stdout, "Image parameter commands\n\n");

   raspicli_display_help(cmdline_commands, cmdline_commands_size);

   fprintf(stdout, "\n");

   return;
}

/**
 * Parse the incoming command line and put resulting parameters in to the state
 *
 * @param argc Number of arguments in command line
 * @param argv Array of pointers to strings from command line
 * @param state Pointer to state structure to assign any discovered parameters to
 * @return Non-0 if failed for some reason, 0 otherwise
 */
static int parse_cmdline(int argc, const char **argv, RASPIVIDYUV_STATE *state)
{
   // Parse the command line arguments.
   // We are looking for --<something> or -<abbreviation of something>

   int valid = 1;
   int i;

   for (i = 1; i < argc && valid; i++)
   {
      int command_id, num_parameters;

      if (!argv[i])
         continue;

      if (argv[i][0] != '-')
      {
         valid = 0;
         continue;
      }

      // Assume parameter is valid until proven otherwise
      valid = 1;

      command_id = raspicli_get_command_id(cmdline_commands, cmdline_commands_size, &argv[i][1], &num_parameters);

      // If we found a command but are missing a parameter, continue (and we will drop out of the loop)
      if (command_id != -1 && num_parameters > 0 && (i + 1 >= argc) )
         continue;

      //  We are now dealing with a command line option
      switch (command_id)
      {
      case CommandTimeout: // Time to run viewfinder/capture
      {
    	  if (sscanf(argv[i + 1], "%d", &state->timeout) == 1)
    	  {

    		  i++;
    	  }
    	  else
    		  valid = 0;
    	  break;
      }

      case CommandFramerate: // fps to record
      {
    	  if (sscanf(argv[i + 1], "%u", &state->framerate) == 1)
    	  {
    		  i++;
    	  }
    	  else
    		  valid = 0;
    	  break;
      }

      default:
      {
    	  // Try parsing for any image specific parameters
    	  // result indicates how many parameters were used up, 0,1,2
    	  // but we adjust by -1 as we have used one already
    	  const char *second_arg = (i + 1 < argc) ? argv[i + 1] : NULL;
    	  int parms_used = (raspicamcontrol_parse_cmdline(&state->camera_parameters, &argv[i][1], second_arg));

    	  // Still unused, try common settings
    	  if (!parms_used)
    		  parms_used = raspicommonsettings_parse_cmdline(&state->common_settings, &argv[i][1], second_arg, &application_help_message);

    	  // Still unused, try preview options
    	  if (!parms_used)
    		  parms_used = raspipreview_parse_cmdline(&state->preview_parameters, &argv[i][1], second_arg);

    	  // If no parms were used, this must be a bad parameters
    	  if (!parms_used)
    		  valid = 0;
    	  else
    		  i += parms_used - 1;

    	  break;
      }
      }
   }

   if (!valid)
   {
      fprintf(stderr, "Invalid command line option (%s)\n", argv[i-1]);
      return 1;
   }

   return 0;
}

/**
 *  buffer header callback function for renderer
 */
static void render_input_callback(MMAL_PORT_T *port, MMAL_BUFFER_HEADER_T *buffer)
{
//	vcos_log_info( "callback_vr_input: buffer->data: %p", buffer->data);
    mmal_buffer_header_release(buffer);
}


/**
 *  buffer header callback function for camera
 *
 *  Callback will dump buffer data to internal buffer
 *
 * @param port Pointer to port from which callback originated
 * @param buffer mmal buffer header pointer
 */
static void camera_buffer_callback(MMAL_PORT_T *port, MMAL_BUFFER_HEADER_T *buffer)
{
	PORT_USERDATA *pData = (PORT_USERDATA *)port->userdata;
	RASPIVIDYUV_STATE *pstate = pData->pstate;
	MMAL_BUFFER_HEADER_T *new_buffer;

	// release buffer back to the pool
	mmal_buffer_header_release(buffer);
	pstate->scannedframe++;

	// and send one back to the port (if still open)
	if (port->is_enabled)
	{
		MMAL_STATUS_T status;

		new_buffer = mmal_queue_get(pData->pstate->camera_pool->queue);
		//      vcos_log_info( "new_buffer: %p", new_buffer->data);

		if (new_buffer) {
			status = mmal_port_send_buffer(port, new_buffer);
		}

		if (!new_buffer || status != MMAL_SUCCESS)
			vcos_log_error("Unable to return a buffer to the camera port");
	}
}


/** Callback from the control port.
 * Component is sending us an event. */
static void decoder_control_callback(MMAL_PORT_T *port, MMAL_BUFFER_HEADER_T *buffer)
{
	vcos_log_info( "decoder_control_callback, buffer->cmd: %u", buffer->cmd);
	if (buffer->cmd == MMAL_EVENT_ERROR) vcos_log_info("decoder error");
	if (buffer->cmd == MMAL_EVENT_EOS) vcos_log_info("decoder MMAL_EVENT_EOS");


	/* Done with the event, recycle it */
	mmal_buffer_header_release(buffer);
}

/** Callback from the input port.
 * Buffer has been consumed and is available to be used again. */
static void decoder_input_callback(MMAL_PORT_T *port, MMAL_BUFFER_HEADER_T *buffer)
{
	vcos_log_info( "decoder_input_callback");
	/* The decoder is done with the data, just recycle the buffer header into its pool */
	mmal_buffer_header_release(buffer);
}

/** Callback from the output port.
 * Buffer has been produced by the port and is available for processing. */
static void decoder_output_callback(MMAL_PORT_T *port, MMAL_BUFFER_HEADER_T *buffer)
{
	vcos_log_info( "decoder_output_callback: buffer->length  %u", buffer->length);
	if (buffer->cmd == MMAL_EVENT_ERROR) vcos_log_info("decoder_output_callback error");
	if (buffer->cmd == MMAL_EVENT_EOS) vcos_log_info("decoder_output_callback MMAL_EVENT_EOS");
	if (buffer->cmd == MMAL_EVENT_FORMAT_CHANGED) vcos_log_info("decoder_output_callback MMAL_EVENT_FORMAT_CHANGED");

	PORT_USERDATA *pData = (PORT_USERDATA *)port->userdata;
	RASPIVIDYUV_STATE *pstate = pData->pstate;
//	pstate->decoder_buffer = buffer;

	mmal_queue_put( pstate->decoder_queue, buffer);
}

static MMAL_STATUS_T create_encoder_component(RASPIVIDYUV_STATE *state) {
	MMAL_WRAPPER_T *encoder = 0;
	MMAL_STATUS_T status;

	status = mmal_wrapper_create(&encoder, MMAL_COMPONENT_DEFAULT_IMAGE_ENCODER);

	if (status != MMAL_SUCCESS)
	{
		vcos_log_error("Unable to create JPEG encoder component");
		goto error;
	}

	if (!encoder->input_num || !encoder->output_num)
	{
		status = MMAL_ENOSYS;
		vcos_log_error("JPEG encoder doesn't have input/output ports");
		goto error;
	}

	state->encoder_component = encoder;

	return status;

	error:

	if (encoder)
		mmal_component_destroy(encoder);

	return status;

}

// mmal_encode_test - Encode a test image and write to file
static void mmal_encode_image(RASPIVIDYUV_STATE *state, MMAL_BUFFER_HEADER_T* in_buffer, const char* filename) // File name
{
	MMAL_WRAPPER_T *encoder;
	MMAL_PORT_T* portIn;
	MMAL_PORT_T* portOut;
	MMAL_BUFFER_HEADER_T* in;
	MMAL_BUFFER_HEADER_T* out;
	MMAL_STATUS_T status;
	unsigned int width;
	unsigned int height;
	int eos = 0;
	int sent = 0;
	int i = 0;
	int idx = 0;
	int outputWritten = 0;
	FILE* outFile;
	int nw;
	int imageByteSize;

	printf("Encoding image %s\n", filename);

	// Configure input

	encoder = state->encoder_component;
	portIn = encoder->input[0];
	encoder->status = MMAL_SUCCESS;
	width = state->common_settings.width;
	height = state->common_settings.height;
	imageByteSize = width*height;

	if (portIn->is_enabled) {
		if (mmal_wrapper_port_disable(portIn) != MMAL_SUCCESS) {
			fprintf(stderr, "Failed to disable input port\n");
			exit(1);
		}
	}

	portIn->format->encoding = MMAL_ENCODING_I420;
	portIn->format->es->video.width = VCOS_ALIGN_UP(width, 32);
	portIn->format->es->video.height = VCOS_ALIGN_UP(height, 16);
	portIn->format->es->video.crop.x = 0;
	portIn->format->es->video.crop.y = 0;
	portIn->format->es->video.crop.width = width;
	portIn->format->es->video.crop.height = height;
	if (mmal_port_format_commit(portIn) != MMAL_SUCCESS) {
		fprintf(stderr, "Failed to commit input port format\n");
		exit(1);
	}

	portIn->buffer_size = portIn->buffer_size_recommended;
	portIn->buffer_num = portIn->buffer_num_recommended;

	if (mmal_wrapper_port_enable(portIn, MMAL_WRAPPER_FLAG_PAYLOAD_ALLOCATE)
			!= MMAL_SUCCESS) {
		fprintf(stderr, "Failed to enable input port\n");
		exit(1);
	}

	printf("- input %4.4s %ux%u\n",
			(char*)&portIn->format->encoding,
			portIn->format->es->video.width, portIn->format->es->video.height);

	// Configure output

	portOut = encoder->output[0];

	if (portOut->is_enabled) {
		if (mmal_wrapper_port_disable(portOut) != MMAL_SUCCESS) {
			fprintf(stderr, "Failed to disable output port\n");
			exit(1);
		}
	}

	portOut->format->encoding = state->encoding;
	if (mmal_port_format_commit(portOut) != MMAL_SUCCESS) {
		fprintf(stderr, "Failed to commit output port format\n");
		exit(1);
	}

	mmal_port_parameter_set_uint32(portOut, MMAL_PARAMETER_JPEG_Q_FACTOR, 95);

	portOut->buffer_size = portOut->buffer_size_recommended;
	portOut->buffer_num = portOut->buffer_num_recommended;

	if (mmal_wrapper_port_enable(portOut, MMAL_WRAPPER_FLAG_PAYLOAD_ALLOCATE)
			!= MMAL_SUCCESS) {
		fprintf(stderr, "Failed to enable output port\n");
		exit(1);
	}

	// Perform the encoding

	outFile = fopen(filename, "w");
	if (!outFile) {
		fprintf(stderr, "Failed to open file %s (%s)\n", filename, strerror(errno));
		exit(1);
	}

	while (!eos) {

		// Send output buffers to be filled with encoded image.
		while (mmal_wrapper_buffer_get_empty(portOut, &out, 0) == MMAL_SUCCESS) {
			if (mmal_port_send_buffer(portOut, out) != MMAL_SUCCESS) {
				fprintf(stderr, "Failed to send buffer\n");
				break;
			}
		}

		// Send image to be encoded.
		if (!sent && mmal_wrapper_buffer_get_empty(portIn, &in, 0) == MMAL_SUCCESS) {
			printf("- sending %u bytes to encoder\n", in->alloc_size);

			uint8_t  *dataIn;
			uint8_t  *data;
			dataIn = in_buffer->data;
			data = in->data;

			for(i = 0; i < in->alloc_size; i++) {
				data[i] = dataIn[idx];
				if (idx < in_buffer->alloc_size) idx++;
			}

			in->length = in->alloc_size;

			if (idx >= in_buffer->alloc_size) {
				in->flags = MMAL_BUFFER_HEADER_FLAG_EOS;
				sent = 1;
			}

			if (mmal_port_send_buffer(portIn, in) != MMAL_SUCCESS) {
				fprintf(stderr, "Failed to send buffer\n");
				break;
			}

		}

		// Get filled output buffers.
		status = mmal_wrapper_buffer_get_full(portOut, &out, 0);
		if (status == MMAL_EAGAIN) {
			// No buffer available, wait and loop.
			vcos_sleep(20);
			continue;
		} else if (status != MMAL_SUCCESS) {
			fprintf(stderr, "Failed to get full buffer\n");
			exit(1);
		}

		printf("- received %i bytes\n", out->length);
		eos = out->flags & MMAL_BUFFER_HEADER_FLAG_EOS;

		nw = fwrite(out->data, 1, out->length, outFile);
		if (nw != out->length) {
			fprintf(stderr, "Failed to write complete buffer\n");
			exit(1);
		}
		outputWritten += nw;

		mmal_buffer_header_release(out);
	}

	mmal_port_flush(portOut);

	fclose(outFile);
	printf("- written %u bytes to %s\n\n", outputWritten, filename);
}

static MMAL_STATUS_T create_decoder_component(RASPIVIDYUV_STATE *state)
{
	vcos_log_info( "create_decoder_component");
	static FILE *source_file;
	MMAL_STATUS_T status = MMAL_EINVAL;
	MMAL_COMPONENT_T *decoder = NULL;
	MMAL_POOL_T *pool_in = NULL, *pool_out = NULL;
	MMAL_BOOL_T eos_sent = MMAL_FALSE, eos_received = MMAL_FALSE;
	MMAL_BUFFER_HEADER_T *buffer;

	source_file = fopen("gradien.png", "rb");
	vcos_log_info( "create_decoder_component: source_file: %p", source_file);

	status = mmal_component_create(MMAL_COMPONENT_DEFAULT_IMAGE_DECODER, &decoder);

	state->decoder_component = decoder;

	/* Set the zero-copy parameter on the output port */
	status = mmal_port_parameter_set_boolean(decoder->output[0], MMAL_PARAMETER_ZERO_COPY, MMAL_TRUE);

	/* Set format of video decoder input port */
	MMAL_ES_FORMAT_T *format_in = decoder->input[0]->format;
	format_in->type = MMAL_ES_TYPE_VIDEO;
	format_in->encoding = MMAL_ENCODING_PNG;
	format_in->es->video.width = 0;
	format_in->es->video.height = 0;
	format_in->es->video.frame_rate.num = 0;
	format_in->es->video.frame_rate.den = 1;
	format_in->es->video.par.num = 1;
	format_in->es->video.par.den = 1;
	status = mmal_port_format_commit(decoder->input[0]);

	MMAL_ES_FORMAT_T *format_out = decoder->output[0]->format;
	format_out->type = MMAL_ES_TYPE_VIDEO;
	format_out->encoding = MMAL_ENCODING_I420;
	format_out->es->video.width = 64;
	format_out->es->video.height = 64;
	status = mmal_port_format_commit(decoder->output[0]);

	/* The format of both ports is now set so we can get their buffer requirements and create
	 * our buffer headers. We use the buffer pool API to create these. */
	decoder->input[0]->buffer_num = 20;
	decoder->input[0]->buffer_size = decoder->input[0]->buffer_size_recommended;
	decoder->output[0]->buffer_num = 10;
	decoder->output[0]->buffer_size = 50000;

	pool_in = mmal_port_pool_create(decoder->input[0],
			decoder->input[0]->buffer_num,
			decoder->input[0]->buffer_size);
	pool_out = mmal_port_pool_create(decoder->output[0],
			decoder->output[0]->buffer_num,
			decoder->output[0]->buffer_size);

	/* Create a queue to store our decoded frame(s). The callback we will get when
	 * a frame has been decoded will put the frame into this queue. */
	state->decoder_queue = mmal_queue_create();
	state->test = 1;

	// Set up our userdata - this is passed though to the callback where we need the information.
//	state->callback_data.pstate = &state;

	/* Store a reference to our context in each port (will be used during callbacks) */
	decoder->input[0]->userdata = (struct MMAL_PORT_USERDATA_T *)&state->callback_data;
	decoder->output[0]->userdata = (struct MMAL_PORT_USERDATA_T *)&state->callback_data;
	decoder->control->userdata = (struct MMAL_PORT_USERDATA_T *)&state->callback_data;


	/* Enable all the input port and the output port.
	 * The callback specified here is the function which will be called when the buffer header
	 * we sent to the component has been processed. */
	status = mmal_port_enable(decoder->control, decoder_control_callback);
	status = mmal_port_enable(decoder->input[0], decoder_input_callback);
	status = mmal_port_enable(decoder->output[0], decoder_output_callback);

	/* Component won't start processing data until it is enabled. */
	status = mmal_component_enable(decoder);

	while ((buffer = mmal_queue_get(pool_out->queue)) != NULL)
	{
		status = mmal_port_send_buffer(decoder->output[0], buffer);
	}

	while (!eos_sent) {
		buffer = mmal_queue_wait(pool_in->queue);

		buffer->length = fread(buffer->data, 1, buffer->alloc_size - 128, source_file);
		buffer->offset = 0;
		if(!buffer->length) eos_sent = MMAL_TRUE;
		buffer->flags = buffer->length ? 0 : MMAL_BUFFER_HEADER_FLAG_EOS;
		buffer->pts = buffer->dts = MMAL_TIME_UNKNOWN;
		status = mmal_port_send_buffer(decoder->input[0], buffer);
	}

	while (!eos_received) {
		vcos_sleep(200);

		/* Get our output frames */
		while ((buffer = mmal_queue_get(state->decoder_queue)) != NULL) {
//			while (state->decoder_buffer != NULL) {
//			buffer = state->decoder_buffer;
			vcos_log_info( "create_decoder_component: mmal_queue_get != null");


			eos_received = buffer->flags & MMAL_BUFFER_HEADER_FLAG_EOS;

			if (buffer->cmd) {
				if (buffer->cmd == MMAL_EVENT_ERROR) {
					vcos_log_info( "create_decoder_component: buffer->cmd: MMAL_EVENT_ERROR	");
				}

				if (buffer->cmd == MMAL_EVENT_FORMAT_CHANGED) {
					vcos_log_info( "create_decoder_component: buffer->cmd: MMAL_EVENT_FORMAT_CHANGED");
					MMAL_EVENT_FORMAT_CHANGED_T *event = mmal_event_format_changed_get(buffer);

					mmal_buffer_header_release(buffer);
					mmal_port_disable(decoder->output[0]);

					//Clear out the queue and release the buffers.
					while(mmal_queue_length(pool_out->queue) < pool_out->headers_num) {
						buffer = mmal_queue_wait(state->decoder_queue);
						mmal_buffer_header_release(buffer);
					}

					//Assume we can't reuse the output buffers, so have to disable, destroy
					//pool, create new pool, enable port, feed in buffers.
					mmal_port_pool_destroy(decoder->output[0], pool_out);

					status = mmal_format_full_copy(decoder->output[0]->format, event->format);
//					decoder->output[0]->format->encoding = MMAL_ENCODING_I420;
					decoder->output[0]->buffer_num = 2;
					decoder->output[0]->buffer_size = decoder->output[0]->buffer_size_recommended;

					if (status == MMAL_SUCCESS)
						status = mmal_port_format_commit(decoder->output[0]);

					mmal_port_enable(decoder->output[0], decoder_output_callback);
					pool_out = mmal_port_pool_create(decoder->output[0], decoder->output[0]->buffer_num, decoder->output[0]->buffer_size);
				} else {
					mmal_buffer_header_release(buffer);
				}

				continue;
			} else {
				// convert rgba to y(uv)

				int k = 0;
				int i = 0;
				int imgW = state->common_settings.width;
				int imgH = state->common_settings.height;
				for (i=0; i<(imgH*imgW); i++) {
					buffer->data[k] = buffer->data[i*4];
					k++;
				}

				state->decoded_gradient_buffer = buffer;
//				mmal_buffer_header_release(buffer);
			}

		}
		/* Send empty buffers to the output port of the decoder */
		while ((buffer = mmal_queue_get(pool_out->queue)) != NULL) {
			status = mmal_port_send_buffer(decoder->output[0], buffer);
		}

	}


	return status;
}

static MMAL_STATUS_T create_render_component(RASPIVIDYUV_STATE *state)
{
	MMAL_COMPONENT_T *render = NULL;
	MMAL_POOL_T *pool;
	MMAL_STATUS_T status;
	MMAL_PORT_T *input;

	render = state->render_component;

	status = mmal_component_create("vc.ril.video_render", &render);
	input = render->input[0];

	state->render_input_port = input;

	input->format->encoding = MMAL_ENCODING_I420_SLICE;
	input->format->es->video.width  = VCOS_ALIGN_UP(state->common_settings.width,  32);
	input->format->es->video.height = VCOS_ALIGN_UP(state->common_settings.height, 16);
	input->format->es->video.crop.x = 0;
	input->format->es->video.crop.y = 0;
	input->format->es->video.crop.width  = state->common_settings.width;
	input->format->es->video.crop.height = state->common_settings.height;
	mmal_port_format_commit(input);

	mmal_component_enable(render);

	mmal_port_parameter_set_boolean(input, MMAL_PARAMETER_ZERO_COPY, MMAL_TRUE);

	input->buffer_size = input->buffer_size_recommended;
	input->buffer_num = RENDER_OUTPUT_BUFFERS_NUM;
	if (input->buffer_num < 2)
		input->buffer_num = RENDER_OUTPUT_BUFFERS_NUM;
	pool = mmal_port_pool_create(input, input->buffer_num, input->buffer_size);

	state->render_pool = pool;

	MMAL_DISPLAYREGION_T param;
	param.hdr.id = MMAL_PARAMETER_DISPLAYREGION;
	param.hdr.size = sizeof(MMAL_DISPLAYREGION_T);

	param.set = MMAL_DISPLAY_SET_LAYER;
	param.layer = 128;    //On top of most things

	param.set |= MMAL_DISPLAY_SET_ALPHA;
	param.alpha = 255;    //0 = transparent, 255 = opaque

	param.set |= (MMAL_DISPLAY_SET_DEST_RECT | MMAL_DISPLAY_SET_FULLSCREEN);
	param.fullscreen = 0;
//	param.dest_rect.x = state->preview_parameters.previewWindow.x;
//	param.dest_rect.y = state->preview_parameters.previewWindow.y;
	param.dest_rect.x = 0;
	param.dest_rect.y = 0;
	param.dest_rect.width = state->preview_parameters.previewWindow.width;
	param.dest_rect.height = state->preview_parameters.previewWindow.width;

	mmal_port_parameter_set(input, &param.hdr);

	mmal_port_enable(input, render_input_callback);

	return status;
}

/**
 * Create the camera component, set up its ports
 *
 * @param state Pointer to state control struct
 *
 * @return MMAL_SUCCESS if all OK, something else otherwise
 *
 */
static MMAL_STATUS_T create_camera_component(RASPIVIDYUV_STATE *state)
{
   MMAL_COMPONENT_T *camera = 0;
   MMAL_ES_FORMAT_T *format;
   MMAL_PORT_T *preview_port = NULL, *video_port = NULL, *still_port = NULL;
   MMAL_STATUS_T status;
   MMAL_POOL_T *pool;

   /* Create the component */
   status = mmal_component_create(MMAL_COMPONENT_DEFAULT_CAMERA, &camera);

   if (status != MMAL_SUCCESS)
   {
      vcos_log_error("Failed to create camera component");
      goto error;
   }

   MMAL_PARAMETER_INT32_T camera_num =
   {{MMAL_PARAMETER_CAMERA_NUM, sizeof(camera_num)}, state->common_settings.cameraNum};

   status = mmal_port_parameter_set(camera->control, &camera_num.hdr);

   if (status != MMAL_SUCCESS)
   {
      vcos_log_error("Could not select camera : error %d", status);
      goto error;
   }

   if (!camera->output_num)
   {
      status = MMAL_ENOSYS;
      vcos_log_error("Camera doesn't have output ports");
      goto error;
   }

   status = mmal_port_parameter_set_uint32(camera->control, MMAL_PARAMETER_CAMERA_CUSTOM_SENSOR_CONFIG, state->common_settings.sensor_mode);

   if (status != MMAL_SUCCESS)
   {
      vcos_log_error("Could not set sensor mode : error %d", status);
      goto error;
   }

   preview_port = camera->output[MMAL_CAMERA_PREVIEW_PORT];
   video_port = camera->output[MMAL_CAMERA_VIDEO_PORT];
   still_port = camera->output[MMAL_CAMERA_CAPTURE_PORT];

   // Enable the camera, and tell it its control callback function
   status = mmal_port_enable(camera->control, default_camera_control_callback);

   if (status != MMAL_SUCCESS)
   {
      vcos_log_error("Unable to enable control port : error %d", status);
      goto error;
   }

   //  set up the camera configuration
   {
      MMAL_PARAMETER_CAMERA_CONFIG_T cam_config =
      {
         { MMAL_PARAMETER_CAMERA_CONFIG, sizeof(cam_config) },
         .max_stills_w = state->common_settings.width,
         .max_stills_h = state->common_settings.height,
         .stills_yuv422 = 0,
         .one_shot_stills = 0,
         .max_preview_video_w = state->common_settings.width,
         .max_preview_video_h = state->common_settings.height,
         .num_preview_video_frames = 3,
         .stills_capture_circular_buffer_height = 0,
         .fast_preview_resume = 0,
         .use_stc_timestamp = MMAL_PARAM_TIMESTAMP_MODE_RESET_STC
      };
      mmal_port_parameter_set(camera->control, &cam_config.hdr);
   }

   // Now set up the port formats

   // Set the encode format on the Preview port
   // HW limitations mean we need the preview to be the same size as the required recorded output

   format = preview_port->format;

   if(state->camera_parameters.shutter_speed > 6000000)
   {
      MMAL_PARAMETER_FPS_RANGE_T fps_range = {{MMAL_PARAMETER_FPS_RANGE, sizeof(fps_range)},
         { 50, 1000 }, {166, 1000}
      };
      mmal_port_parameter_set(preview_port, &fps_range.hdr);
   }
   else if(state->camera_parameters.shutter_speed > 1000000)
   {
      MMAL_PARAMETER_FPS_RANGE_T fps_range = {{MMAL_PARAMETER_FPS_RANGE, sizeof(fps_range)},
         { 166, 1000 }, {999, 1000}
      };
      mmal_port_parameter_set(preview_port, &fps_range.hdr);
   }

   //enable dynamic framerate if necessary
   if (state->camera_parameters.shutter_speed)
   {
      if (state->framerate > 1000000./state->camera_parameters.shutter_speed)
      {
         state->framerate=0;
         if (state->common_settings.verbose)
            fprintf(stderr, "Enable dynamic frame rate to fulfil shutter speed requirement\n");
      }
   }

   format->encoding = MMAL_ENCODING_OPAQUE;
   format->es->video.width = VCOS_ALIGN_UP(state->common_settings.width, 32);
   format->es->video.height = VCOS_ALIGN_UP(state->common_settings.height, 16);
   format->es->video.crop.x = 0;
   format->es->video.crop.y = 0;
   format->es->video.crop.width = state->common_settings.width;
   format->es->video.crop.height = state->common_settings.height;
   format->es->video.frame_rate.num = PREVIEW_FRAME_RATE_NUM;
   format->es->video.frame_rate.den = PREVIEW_FRAME_RATE_DEN;

   status = mmal_port_format_commit(preview_port);

   if (status != MMAL_SUCCESS)
   {
      vcos_log_error("camera viewfinder format couldn't be set");
      goto error;
   }

   // Set the encode format on the video  port

   format = video_port->format;

   if(state->camera_parameters.shutter_speed > 6000000)
   {
      MMAL_PARAMETER_FPS_RANGE_T fps_range = {{MMAL_PARAMETER_FPS_RANGE, sizeof(fps_range)},
         { 50, 1000 }, {166, 1000}
      };
      mmal_port_parameter_set(video_port, &fps_range.hdr);
   }
   else if(state->camera_parameters.shutter_speed > 1000000)
   {
      MMAL_PARAMETER_FPS_RANGE_T fps_range = {{MMAL_PARAMETER_FPS_RANGE, sizeof(fps_range)},
         { 167, 1000 }, {999, 1000}
      };
      mmal_port_parameter_set(video_port, &fps_range.hdr);
   }

//   if (state->useRGB)
//   {
//      format->encoding = mmal_util_rgb_order_fixed(still_port) ? MMAL_ENCODING_RGB24 : MMAL_ENCODING_BGR24;
//      format->encoding_variant = 0;  //Irrelevant when not in opaque mode
//   }
//   else
//   {
      format->encoding = MMAL_ENCODING_I420;
      format->encoding_variant = MMAL_ENCODING_I420;
//   }

   format->es->video.width = VCOS_ALIGN_UP(state->common_settings.width, 32);
   format->es->video.height = VCOS_ALIGN_UP(state->common_settings.height, 16);
   format->es->video.crop.x = 0;
   format->es->video.crop.y = 0;
   format->es->video.crop.width = state->common_settings.width;
   format->es->video.crop.height = state->common_settings.height;
   format->es->video.frame_rate.num = state->framerate;
   format->es->video.frame_rate.den = VIDEO_FRAME_RATE_DEN;

   status = mmal_port_format_commit(video_port);

   if (status != MMAL_SUCCESS)
   {
      vcos_log_error("camera video format couldn't be set");
      goto error;
   }

   // Ensure there are enough buffers to avoid dropping frames
//   if (video_port->buffer_num < state->common_settings.height)
//      video_port->buffer_num = state->common_settings.height;
   if (video_port->buffer_num < format->es->video.height)
         video_port->buffer_num = format->es->video.height;


   status = mmal_port_parameter_set_boolean(video_port, MMAL_PARAMETER_ZERO_COPY, MMAL_TRUE);
   if (status != MMAL_SUCCESS)
   {
      vcos_log_error("Failed to select zero copy");
      goto error;
   }

   // Set the encode format on the still  port

   format = still_port->format;

   format->encoding = MMAL_ENCODING_OPAQUE;
   format->encoding_variant = MMAL_ENCODING_I420;

   format->es->video.width = VCOS_ALIGN_UP(state->common_settings.width, 32);
   format->es->video.height = VCOS_ALIGN_UP(state->common_settings.height, 16);
   format->es->video.crop.x = 0;
   format->es->video.crop.y = 0;
   format->es->video.crop.width = state->common_settings.width;
   format->es->video.crop.height = state->common_settings.height;
   format->es->video.frame_rate.num = 0;
   format->es->video.frame_rate.den = 1;

   status = mmal_port_format_commit(still_port);

   if (status != MMAL_SUCCESS)
   {
      vcos_log_error("camera still format couldn't be set");
      goto error;
   }

   /* Ensure there are enough buffers to avoid dropping frames */
   if (still_port->buffer_num < STILL_OUTPUT_BUFFERS_NUM)
      still_port->buffer_num = STILL_OUTPUT_BUFFERS_NUM;

   /* Enable component */
   status = mmal_component_enable(camera);

   if (status != MMAL_SUCCESS)
   {
      vcos_log_error("camera component couldn't be enabled");
      goto error;
   }

   raspicamcontrol_set_all_parameters(camera, &state->camera_parameters);

   /* Create pool of buffer headers for the output port to consume */
   pool = mmal_port_pool_create(video_port, video_port->buffer_num, video_port->buffer_size);

   if (!pool)
   {
      vcos_log_error("Failed to create buffer header pool for camera still port %s", still_port->name);
   }

   state->camera_pool = pool;
   state->camera_component = camera;
   state->scannedframe = 0;

   if (state->common_settings.verbose)
      fprintf(stderr, "Camera component done\n");

   return status;

error:

   if (camera)
      mmal_component_destroy(camera);

   return status;
}

/**
 * Destroy the camera component
 *
 * @param state Pointer to state control struct
 *
 */
static void destroy_camera_component(RASPIVIDYUV_STATE *state)
{
   if (state->camera_component)
   {
      mmal_component_destroy(state->camera_component);
      state->camera_component = NULL;
   }
}

static void destroy_render_component(RASPIVIDYUV_STATE *state) {

	mmal_component_destroy(state->render_component);

}

/**
 * Destroy the encoder component
 *
 * @param state Pointer to state control struct
 *
 */
static void destroy_encoder_component(RASPIVIDYUV_STATE *state)
{

   if (state->encoder_component)
   {
	   mmal_wrapper_destroy(state->encoder_component);
      state->encoder_component = NULL;
   }
}


void signal_handler(int signal_number){
	if (signal_number == SIGUSR1){
		signal_USR1 = 1;
	}
	if (signal_number == SIGUSR2){
		signal_USR2 = 1;
	}
}

/**
 * main
 */
int main(int argc, const char **argv)
{
   // Our main data storage vessel..
   RASPIVIDYUV_STATE state;
   int exit_code = EX_OK;

   MMAL_STATUS_T status = MMAL_SUCCESS;
   MMAL_PORT_T *camera_preview_port = NULL;
   MMAL_PORT_T *camera_video_port = NULL;
   MMAL_PORT_T *camera_still_port = NULL;
   MMAL_PORT_T *preview_input_port = NULL;
   MMAL_PORT_T *render_input_port = NULL;

   bcm_host_init();

   // Register our application with the logging system
   vcos_log_register("RaspiVid", VCOS_LOG_CATEGORY);

   // enable signal USR1&2
   signal(SIGINT, default_signal_handler);
   signal(SIGUSR1, signal_handler);
   signal(SIGUSR2, signal_handler);


   set_app_name(argv[0]);

   // Do we have any parameters
   if (argc == 1)
   {
      display_valid_parameters(basename(get_app_name()), &application_help_message);
      exit(EX_USAGE);
   }

   default_status(&state);

   // Parse the command line and put options in to our status structure
   if (parse_cmdline(argc, argv, &state))
   {
      status = -1;
      exit(EX_USAGE);
   }

   if (state.timeout == -1)
      state.timeout = 5000;

   // Setup for sensor specific parameters, only set W/H settings if zero on entry
   get_sensor_defaults(state.common_settings.cameraNum, state.common_settings.camera_name,
                       &state.common_settings.width, &state.common_settings.height);

   // Set up our userdata - this is passed though to the callback where we need the information.
   state.callback_data.pstate = &state;

   if ((status = create_camera_component(&state)) != MMAL_SUCCESS)
   {
      vcos_log_error("%s: Failed to create camera component", __func__);
      exit_code = EX_SOFTWARE;
   }
   else if ((status = raspipreview_create(&state.preview_parameters)) != MMAL_SUCCESS)
   {
      vcos_log_error("%s: Failed to create preview component", __func__);
      destroy_camera_component(&state);
      exit_code = EX_SOFTWARE;
   }
   else if ((status = create_decoder_component(&state)) != MMAL_SUCCESS)
   {
	   vcos_log_error("%s: Failed to create decoder component", __func__);
	   exit_code = EX_SOFTWARE;
   }
   else if ((status = create_render_component(&state)) != MMAL_SUCCESS)
   {
	   vcos_log_error("%s: Failed to create render component", __func__);
	   destroy_render_component(&state);
	   exit_code = EX_SOFTWARE;
   }
   else if ((status = create_encoder_component(&state)) != MMAL_SUCCESS)
      {
   	   vcos_log_error("%s: Failed to create encoder component", __func__);
   	   destroy_encoder_component(&state);
   	   exit_code = EX_SOFTWARE;
      }
   else
   {
      if (state.common_settings.verbose)
         fprintf(stderr, "Starting component connection stage\n");

      camera_preview_port = state.camera_component->output[MMAL_CAMERA_PREVIEW_PORT];
      camera_video_port   = state.camera_component->output[MMAL_CAMERA_VIDEO_PORT];
      camera_still_port   = state.camera_component->output[MMAL_CAMERA_CAPTURE_PORT];
      preview_input_port  = state.preview_parameters.preview_component->input[0];
      render_input_port   = state.render_input_port;


      if (state.preview_parameters.wantPreview )
      {
    	  // Connect camera to preview
    	  status = connect_ports(camera_preview_port, preview_input_port, &state.preview_connection);

    	  if (status != MMAL_SUCCESS)
    		  state.preview_connection = NULL;
      }
      else
      {
    	  status = MMAL_SUCCESS;
      }

      if (status == MMAL_SUCCESS)
      {
    	  state.callback_data.file_handle = stdout;
    	  state.callback_data.abort = 0;

    	  camera_video_port->userdata = (struct MMAL_PORT_USERDATA_T *)&state.callback_data;

    	  // Enable the camera video port and tell it its callback function
    	  status = mmal_port_enable(camera_video_port, camera_buffer_callback);

    	  if (status != MMAL_SUCCESS)
    	  {
    		  vcos_log_error("Failed to setup camera output");
    		  goto error;
    	  }

    	  // Send all the buffers to the camera video port
    	  int num = mmal_queue_length(state.camera_pool->queue);
    	  int q;
    	  for (q=0; q<num; q++)
    	  {
    		  MMAL_BUFFER_HEADER_T *buffer = mmal_queue_get(state.camera_pool->queue);

    		  if (!buffer)
    			  vcos_log_error("Unable to get a required buffer %d from pool queue", q);

    		  if (mmal_port_send_buffer(camera_video_port, buffer)!= MMAL_SUCCESS)
    			  vcos_log_error("Unable to send a buffer to camera video port (%d)", q);
    	  }


    	  // Write something into the render buffers, will not use the U and V channels, so put no color (0x7F).
    	  for (q=0; q<RENDER_OUTPUT_BUFFERS_NUM; q++) {
    		  MMAL_BUFFER_HEADER_T *buffer = mmal_queue_get(state.render_pool->queue);
    		  memset(buffer->data, 0x7F, buffer->alloc_size);
    		  buffer->length = buffer->alloc_size;
    		  mmal_port_send_buffer(state.render_input_port, buffer);
    	  }


         mmal_port_parameter_set_boolean(camera_video_port, MMAL_PARAMETER_CAPTURE, 1);

         // Rrendering loop:

         MMAL_BUFFER_HEADER_T **camerabufferarray;
         MMAL_BUFFER_HEADER_T **renderbufferarray;
         MMAL_POOL_T *renderpool = state.render_pool;
         MMAL_POOL_T *camerapool = state.camera_pool;
         MMAL_BUFFER_HEADER_T *gradientbuffer = state.decoded_gradient_buffer;
         uint8_t *gradientdata = gradientbuffer->data;
         uint32_t videoWidth = camera_video_port->format->es->video.width;
         uint32_t videoHeight = camera_video_port->format->es->video.height;
         uint32_t camera_headers_num = camerapool->headers_num;
         uint32_t render_headers_num = renderpool->headers_num;
         MMAL_BUFFER_HEADER_T *renderbuffer;
         MMAL_BUFFER_HEADER_T *camerabuffer;
         uint8_t  *dataPayload;
         uint8_t  *dataRender;
         unsigned int cameraFrameNumber;
         unsigned int renderFrameNumber = 0;
         int imageYByteSize = videoWidth*videoHeight;
         int imageYUVByteSize;

         int timeIdx = 0;
         int i=0, j=0;
         int startTime = (int)(vcos_getmicrosecs()/1000);
         int elapsedTime = 0;
         int keepRunning = 1;
         int bytes_written = 0;

         camerabufferarray = camerapool->header;
         renderbufferarray = renderpool->header;

         while(keepRunning) {

        	 // time scan effect enable/disable on sigusr1
        	 if (signal_USR1){
        		 state.timeScanRendering = ! state.timeScanRendering;
        		 signal_USR1 = 0;
        	 }

        	 // if we had a signal usr2
        	 if (signal_USR2){

        		 // send renderbuffer on stdout
        		 for( i=0; i<render_headers_num; i+=2) {
        			 renderbuffer = renderbufferarray[(renderFrameNumber+i)%render_headers_num];
        			 mmal_buffer_header_mem_lock(renderbuffer);
        			 bytes_written = fwrite(renderbuffer->data, 1, imageYByteSize, stdout);
        			 mmal_buffer_header_mem_unlock(renderbuffer);
        		 }

        		 // plays an animation of the last computed frames.
        		 for( i=render_headers_num-1; i>=0; i--) {
        			 renderbuffer = renderbufferarray[(renderFrameNumber+i)%render_headers_num];
        			 renderbuffer->length = renderbuffer->alloc_size;
        			 mmal_port_send_buffer(state.render_input_port, renderbuffer);
        			 vcos_sleep(40);
        		 }
        		 for( i=0; i<render_headers_num; i++) {
        			 renderbuffer = renderbufferarray[(renderFrameNumber+i)%render_headers_num];
        			 renderbuffer->length = renderbuffer->alloc_size;
        			 mmal_port_send_buffer(state.render_input_port, renderbuffer);
        			 vcos_sleep(40);
        		 }

        		 vcos_sleep(3000);

        		 signal_USR2 = 0;
        	 }

        	 // compute the frame to be render
        	 cameraFrameNumber = state.scannedframe;
        	 renderbuffer = renderbufferarray[renderFrameNumber%render_headers_num];

        	 if (renderbuffer) {
        		 dataRender = renderbuffer->data;

        		 if (state.timeScanRendering){
        			 // copy the camera buffer to the render buffer according to the wanted frame-delay
        			 for (i = 0; i < videoHeight; i++){
        				 timeIdx = (cameraFrameNumber - i - 1) % camera_headers_num;
        				 camerabuffer = camerabufferarray[timeIdx];
        				 dataPayload = (uint8_t *)camerabuffer->priv->payload;
        				 for (j = 0; j < videoWidth; j++){
        					 dataRender[j+(i*videoWidth)] = dataPayload[j+(i*videoWidth)];
        				 }
        			 }
        		 } else {
        			 // copy the  last camera buffer to the render buffer
        			 for (i = 0; i < imageYByteSize; i++){
        				 timeIdx = (cameraFrameNumber - 1) % camera_headers_num;
        				 camerabuffer = camerabufferarray[timeIdx];
        				 dataPayload = (uint8_t *)camerabuffer->priv->payload;
        				 dataRender[i] = dataPayload[i];
        				 //        				 dataRender[i] = gradientdata[i];
        			 }
        		 }

        		 // after timeout, send a copy of renderbuffer to the encoder then write the file
        		 vcos_log_info( "time %i", (int)(vcos_getmicrosecs()/1000000) - startTime );
        		 elapsedTime = (int)(vcos_getmicrosecs()/1000) - startTime;
        		 if (state.timeout != 0 && elapsedTime > state.timeout){
        			 keepRunning = 0;
        			 char *filename = state.common_settings.filename;

        			 mmal_encode_image(&state, renderbuffer, filename);
        			 vcos_sleep(1000);
        		 }

        		 //send the render buffer to the render port
        		 renderbuffer->length = renderbuffer->alloc_size;
        		 mmal_port_send_buffer(state.render_input_port, renderbuffer);
        		 renderFrameNumber++;
        	 }

        	 // sleep to not saturate the cpu (it changes the output framerate)
        	 vcos_sleep(20);
         }


      }
      else
      {
    	  mmal_status_to_int(status);
    	  vcos_log_error("%s: Failed to connect camera to preview", __func__);
      }

      error:

	  mmal_status_to_int(status);

	  if (state.common_settings.verbose)
		  fprintf(stderr, "Closing down\n");

	  // Disable all our ports that are not handled by connections
	  check_disable_port(camera_video_port);

	  if (state.preview_parameters.wantPreview && state.preview_connection)
		  mmal_connection_destroy(state.preview_connection);

	  if (state.preview_parameters.preview_component)
		  mmal_component_disable(state.preview_parameters.preview_component);

	  if (state.camera_component)
		  mmal_component_disable(state.camera_component);

	  // Can now close our file. Note disabling ports may flush buffers which causes
	  // problems if we have already closed the file!
	  if (state.callback_data.file_handle && state.callback_data.file_handle != stdout)
		  fclose(state.callback_data.file_handle);
	  if (state.callback_data.pts_file_handle && state.callback_data.pts_file_handle != stdout)
		  fclose(state.callback_data.pts_file_handle);

	  raspipreview_destroy(&state.preview_parameters);
	  destroy_camera_component(&state);
	  destroy_render_component(&state);
	  destroy_encoder_component(&state);

	  if (state.common_settings.verbose)
		  fprintf(stderr, "Close down completed, all components disconnected, disabled and destroyed\n\n");
   }

   if (status != MMAL_SUCCESS)
	   raspicamcontrol_check_configuration(128);

   return exit_code;
}

//        		 // test with 2 color ordered dithering
//        		 for (int i = 0; i < videoHeight; i++){
//        			 for (int j = 0; j < videoWidth; j++){
//        				 idx = (i*videoWidth)+j;
//        				 timeIdx = gradientdata[idx];
//        				 timeIdx = (timeIdx+frameNumber+9) % headers_num;
//        				 camerabuffer = camerabufferarray[timeIdx];
//        				 dataPayload = (uint8_t *)camerabuffer->priv->payload;
//
//        				 dataRender[idx] = dataPayload[idx]/4 > (pattern[j & 7][i & 7]) ? 255 : 0;
//
//        			 }
//        		 }

//        		 // test averaging
//        		 for (int i = 0; i < imageByteSize; i++){
//        			 av = 0;
//        			 for (int j=0; j<headers_num; j++){
//        				 timeIdx = ((frameNumber-j)) % headers_num;
//        				 camerabuffer = camerabufferarray[timeIdx];
//        				 dataPayload = (uint8_t *)camerabuffer->priv->payload;
//        				 av += dataPayload[i];
////        				 av = dataPayload[i] > av ? dataPayload[i] : av;
//        			 }
//        			 dataRender[i] = av;
//        		 }



